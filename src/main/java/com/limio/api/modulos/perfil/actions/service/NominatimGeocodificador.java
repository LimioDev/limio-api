package com.limio.api.modulos.perfil.actions.service;

import java.net.http.HttpClient;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.limio.api.modulos.perfil.endereco.EnderecoParaGeocodificar;
import com.limio.api.modulos.perfil.enums.Uf;
import com.limio.api.modulos.perfil.excecao.GeocodificacaoIndisponivelException;
import com.limio.api.modulos.perfil.geo.Coordenadas;

/**
 * Geocodificação do município-base (TICKET-0054) e do endereço salvo (TICKET-0050) via Nominatim
 * (OpenStreetMap) — decisão do TICKET-0054, a validar com
 * o tech lead. O servidor público tem política de uso (https://operations.osmfoundation.org/policies/nominatim/):
 * no máximo 1 requisição por segundo, User-Agent que identifique o app, cache do lado de quem consulta e
 * atribuição ao OpenStreetMap. Por isso URL e User-Agent vêm de configuração (trocar de provedor ou subir
 * instância própria não exige versão nova) e o usecase só chama aqui pra município que nenhum perfil
 * resolveu ainda.
 *
 * <p>Endereço não tem cache (cada rua+número é quase único), então toda consulta passa por um limitador simples:
 * as chamadas desta instância saem em fila, com no mínimo {@code geocodificacao.nominatim.intervalo-minimo}
 * (padrão 1s) entre o início de uma e da seguinte. Quem não consegue a vez dentro do {@code timeout} desiste com
 * {@link GeocodificacaoIndisponivelException} (503) em vez de bater no 429. Vale por instância da aplicação; com
 * várias réplicas, ou com instância própria do Nominatim, ajuste ou zere o intervalo. Intervalo zero desliga.
 */
@Component
public class NominatimGeocodificador implements Geocodificador {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodificador.class);

    /**
     * {@code place_rank} do Nominatim: 16 é município (limite administrativo); 15 é Brasília (o DF não tem
     * municípios). Acima disso é distrito ou bairro com o nome digitado — "Centro"/RJ, por exemplo, caía num
     * distrito de Nova Friburgo.
     */
    private static final int RANK_MAXIMO_MUNICIPIO = 16;

    /** {@code place_rank} 26 é rua; 30 é número de casa. Abaixo disso é bairro, cidade ou região. */
    private static final int RANK_MINIMO_RUA = 26;

    private final RestClient restClient;
    private final Duration intervaloMinimo;
    private final Duration esperaMaxima;

    /** Fila das consultas (justa: quem chegou antes sai antes); {@code ultimaConsultaEm} só muda com ela travada. */
    private final ReentrantLock fila = new ReentrantLock(true);
    private long ultimaConsultaEm;
    private boolean jaConsultou;

    /** Sem limitador: para quem usa instância própria do Nominatim e nos testes do parser. */
    public NominatimGeocodificador(String url, String userAgent, Duration timeout) {
        this(url, userAgent, timeout, Duration.ZERO);
    }

    @Autowired
    public NominatimGeocodificador(@Value("${geocodificacao.nominatim.url}") String url,
            @Value("${geocodificacao.nominatim.user-agent}") String userAgent,
            @Value("${geocodificacao.timeout}") Duration timeout,
            @Value("${geocodificacao.nominatim.intervalo-minimo:1s}") Duration intervaloMinimo) {
        this.intervaloMinimo = intervaloMinimo;
        this.esperaMaxima = timeout;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Vazio quando o Nominatim não acha município com esse nome ali. A indisponibilidade inclui o 429 por
     * excesso de consultas.
     */
    @Override
    public Optional<Coordenadas> localizarMunicipio(String municipio, Uf uf) {
        // Variáveis de URI (não concatenação): o nome digitado é codificado por inteiro, então um "&" no
        // texto não vira parâmetro novo na consulta.
        ResultadoNominatim[] resultados = consultar(
                "/search?city={municipio}&state={estado}&countrycodes=br&featureType=city&format=jsonv2&limit=1",
                municipio, uf.getNome());
        if (resultados == null || resultados.length == 0) {
            return Optional.empty();
        }
        ResultadoNominatim resultado = resultados[0];
        if (resultado.placeRank() == null || resultado.lat() == null || resultado.lon() == null) {
            throw respostaInvalida(resultado);
        }
        if (resultado.placeRank() > RANK_MAXIMO_MUNICIPIO) {
            return Optional.empty();
        }
        return Optional.of(coordenadasDe(resultado));
    }

    /**
     * Busca estruturada {@code street} ("número rua", a ordem que o Nominatim espera) + {@code city} +
     * {@code state}. Só vale resultado de rua ou mais preciso ({@code place_rank >= 26}) e na UF pedida: sem
     * isso o Nominatim devolve o centro da cidade ou um homônimo de outro estado, e gravar isso como se fosse
     * o endereço enganaria o cálculo de distância.
     *
     * <p>Limite conhecido: {@code place_rank} 26 aceita a rua sem o número, e então o ponto é o centro da via —
     * em rua longa pode estar a quilômetros da porta. Aceito por ora (o uso é distância aproximada); ver
     * TICKET-0050.
     */
    @Override
    public Optional<Coordenadas> localizarEndereco(EnderecoParaGeocodificar endereco) {
        ResultadoNominatim[] resultados = consultar(
                "/search?street={rua}&city={cidade}&state={estado}&countrycodes=br&format=jsonv2"
                        + "&addressdetails=1&limit=1",
                endereco.numero() + " " + endereco.rua(), endereco.cidade(), endereco.uf().getNome());
        if (resultados == null || resultados.length == 0) {
            return Optional.empty();
        }
        ResultadoNominatim resultado = resultados[0];
        if (resultado.placeRank() == null || resultado.lat() == null || resultado.lon() == null
                || resultado.address() == null || resultado.address().state() == null) {
            throw respostaInvalida(resultado);
        }
        if (resultado.placeRank() < RANK_MINIMO_RUA
                || !mesmoTexto(resultado.address().state(), endereco.uf().getNome())) {
            return Optional.empty();
        }
        return Optional.of(coordenadasDe(resultado));
    }

    /**
     * Espera a vez e, se a última consulta foi há menos que o intervalo, o resto dele. Desiste (503) se a fila
     * não andar dentro do {@code timeout} ou se a thread for interrompida.
     */
    private void aguardarVez() {
        if (intervaloMinimo.isZero() || intervaloMinimo.isNegative()) {
            return;
        }
        try {
            if (!fila.tryLock(esperaMaxima.toNanos(), TimeUnit.NANOSECONDS)) {
                log.warn("geocodificação: fila cheia, desistindo após {}", esperaMaxima);
                throw new GeocodificacaoIndisponivelException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeocodificacaoIndisponivelException();
        }
        try {
            if (jaConsultou) {
                long faltam = intervaloMinimo.toNanos() - (System.nanoTime() - ultimaConsultaEm);
                if (faltam > 0) {
                    TimeUnit.NANOSECONDS.sleep(faltam);
                }
            }
            jaConsultou = true;
            ultimaConsultaEm = System.nanoTime();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeocodificacaoIndisponivelException();
        } finally {
            fila.unlock();
        }
    }

    private ResultadoNominatim[] consultar(String uri, Object... variaveis) {
        aguardarVez();
        try {
            return restClient.get().uri(uri, variaveis).retrieve().body(ResultadoNominatim[].class);
        } catch (RestClientException e) {
            log.warn("geocodificação indisponível ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            throw new GeocodificacaoIndisponivelException();
        }
    }

    private static Coordenadas coordenadasDe(ResultadoNominatim resultado) {
        try {
            return new Coordenadas(Double.parseDouble(resultado.lat()), Double.parseDouble(resultado.lon()));
        } catch (IllegalArgumentException e) {
            // NumberFormatException (texto não numérico) ou coordenada fora da faixa (Coordenadas).
            throw respostaInvalida(resultado);
        }
    }

    /** Sem caixa e sem acento: o Nominatim devolve "São Paulo", mas a grafia pode variar ("Sao Paulo"). */
    private static boolean mesmoTexto(String a, String b) {
        return semAcento(a).equalsIgnoreCase(semAcento(b));
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /**
     * Resposta 200 que não dá pra usar (instância alternativa configurada, mudança de formato): é falha do
     * provedor, não do que o usuário digitou — mesmo 503 de serviço fora do ar.
     */
    private static GeocodificacaoIndisponivelException respostaInvalida(ResultadoNominatim resultado) {
        log.warn("geocodificação devolveu resultado fora do formato esperado: {}", resultado);
        return new GeocodificacaoIndisponivelException();
    }

    /**
     * Só o que interessa da resposta {@code jsonv2} — o Nominatim manda lat/lon como texto. {@code placeRank}
     * em {@code Integer}: campo ausente vira {@code null} (resposta inválida), não 0 (que passaria por município).
     * {@code address} só vem com {@code addressdetails=1} (busca de endereço).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResultadoNominatim(String lat, String lon, @JsonProperty("place_rank") Integer placeRank,
            Endereco address) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Endereco(String state) {
        }
    }
}
