package com.limio.api.modulos.perfil.actions.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.limio.api.modulos.perfil.excecao.GeocodificacaoIndisponivelException;
import com.limio.api.modulos.perfil.perfilprestador.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.enums.Uf;

/**
 * Geocodificação do município-base via Nominatim (OpenStreetMap) — decisão do TICKET-0054, a validar com
 * o tech lead. O servidor público tem política de uso (https://operations.osmfoundation.org/policies/nominatim/):
 * no máximo 1 requisição por segundo, User-Agent que identifique o app, cache do lado de quem consulta e
 * atribuição ao OpenStreetMap. Por isso URL e User-Agent vêm de configuração (trocar de provedor ou subir
 * instância própria não exige versão nova) e o usecase só chama aqui pra município que nenhum perfil
 * resolveu ainda.
 */
@Component
public class GeocodificacaoService {

    private static final Logger log = LoggerFactory.getLogger(GeocodificacaoService.class);

    /**
     * {@code place_rank} do Nominatim: 16 é município (limite administrativo); 15 é Brasília (o DF não tem
     * municípios). Acima disso é distrito ou bairro com o nome digitado — "Centro"/RJ, por exemplo, caía num
     * distrito de Nova Friburgo.
     */
    private static final int RANK_MAXIMO_MUNICIPIO = 16;

    private final RestClient restClient;

    public GeocodificacaoService(@Value("${geocodificacao.nominatim.url}") String url,
            @Value("${geocodificacao.nominatim.user-agent}") String userAgent,
            @Value("${geocodificacao.timeout}") Duration timeout) {
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
     * Centro do município na UF. Vazio quando o Nominatim não acha município com esse nome ali.
     *
     * @throws GeocodificacaoIndisponivelException serviço fora do ar, timeout, resposta de erro (inclusive
     *         429 por excesso de consultas) ou resposta 200 fora do formato esperado
     */
    public Optional<Coordenadas> localizarMunicipio(String municipio, Uf uf) {
        ResultadoNominatim[] resultados;
        try {
            // Variáveis de URI (não concatenação): o nome digitado é codificado por inteiro, então um "&" no
            // texto não vira parâmetro novo na consulta.
            resultados = restClient.get()
                    .uri("/search?city={municipio}&state={estado}&countrycodes=br&featureType=city&format=jsonv2&limit=1",
                            municipio, uf.getNome())
                    .retrieve()
                    .body(ResultadoNominatim[].class);
        } catch (RestClientException e) {
            log.warn("geocodificação indisponível ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            throw new GeocodificacaoIndisponivelException();
        }
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
        try {
            return Optional.of(new Coordenadas(Double.parseDouble(resultado.lat()),
                    Double.parseDouble(resultado.lon())));
        } catch (IllegalArgumentException e) {
            // NumberFormatException (texto não numérico) ou coordenada fora da faixa (Coordenadas).
            throw respostaInvalida(resultado);
        }
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
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResultadoNominatim(String lat, String lon, @JsonProperty("place_rank") Integer placeRank) {
    }
}
