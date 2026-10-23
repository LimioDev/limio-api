package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.limio.api.modulos.perfil.actions.service.NominatimGeocodificador;
import com.limio.api.modulos.perfil.endereco.EnderecoParaGeocodificar;
import com.limio.api.modulos.perfil.enums.Uf;
import com.limio.api.modulos.perfil.excecao.GeocodificacaoIndisponivelException;
import com.limio.api.modulos.perfil.perfilprestador.Coordenadas;
import com.sun.net.httpserver.HttpServer;

/**
 * {@link NominatimGeocodificador} contra um Nominatim falso (servidor HTTP do próprio JDK na porta local):
 * exercita a URL montada, o User-Agent e a leitura do JSON de verdade, sem rede e sem Spring context.
 */
class NominatimGeocodificadorTest {

    private static final String USER_AGENT = "limio-api-teste/1.0";

    private HttpServer servidor;
    private volatile String consultaRecebida;
    private volatile String userAgentRecebido;

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private NominatimGeocodificador nominatimRespondendo(int status, String corpo) throws IOException {
        return nominatimRespondendo(status, corpo, Duration.ZERO, Duration.ofSeconds(2));
    }

    private NominatimGeocodificador nominatimRespondendo(int status, String corpo, Duration atraso,
            Duration timeout) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/search", troca -> {
            try {
                Thread.sleep(atraso.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            consultaRecebida = troca.getRequestURI().getRawQuery();
            userAgentRecebido = troca.getRequestHeaders().getFirst("User-Agent");
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            troca.sendResponseHeaders(status, bytes.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        });
        servidor.start();
        return new NominatimGeocodificador("http://127.0.0.1:" + servidor.getAddress().getPort(), USER_AGENT,
                timeout);
    }

    private static String resultado(String lat, String lon, int placeRank) {
        return "[{\"place_id\":1,\"lat\":\"" + lat + "\",\"lon\":\"" + lon + "\",\"place_rank\":" + placeRank
                + ",\"display_name\":\"Campinas, São Paulo, Brasil\"}]";
    }

    @Test
    void deveDevolverAsCoordenadasDoMunicipioConsultandoComUfPorExtensoEUserAgentDoApp() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200, resultado("-22.9056391", "-47.0595640", 16));

        Optional<Coordenadas> coordenadas = servico.localizarMunicipio("Campinas", Uf.SP);

        assertThat(coordenadas).contains(new Coordenadas(-22.9056391, -47.059564));
        assertThat(consultaRecebida)
                .contains("city=Campinas")
                .contains("state=S%C3%A3o%20Paulo")
                .contains("countrycodes=br")
                .contains("format=jsonv2")
                .contains("limit=1");
        assertThat(userAgentRecebido).isEqualTo(USER_AGENT);
    }

    @Test
    void deveCodificarONomeDigitadoInteiroSemAbrirParametroNovo() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200, "[]");

        servico.localizarMunicipio("Campinas&limit=40", Uf.SP);

        assertThat(consultaRecebida).contains("city=Campinas%26limit%3D40").doesNotContain("limit=40");
    }

    @Test
    void deveAceitarBrasiliaQueVemComRankDeCidade() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200, resultado("-15.7939869", "-47.8828", 15));

        assertThat(servico.localizarMunicipio("Brasília", Uf.DF)).contains(new Coordenadas(-15.7939869, -47.8828));
    }

    @Test
    void deveDevolverVazioQuandoNaoAchaNada() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200, "[]");

        assertThat(servico.localizarMunicipio("Xyzabc", Uf.SP)).isEmpty();
    }

    @Test
    void deveDevolverVazioQuandoOResultadoEDistritoOuBairroENaoMunicipio() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200, resultado("-22.28", "-42.5325303", 18));

        assertThat(servico.localizarMunicipio("Centro", Uf.RJ)).isEmpty();
    }

    @Test
    void deveFalharComoIndisponivelQuandoONominatimRespondeErro() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(429, "{\"error\":\"too many requests\"}");

        assertThatThrownBy(() -> servico.localizarMunicipio("Campinas", Uf.SP))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    @Test
    void deveFalharComoIndisponivelQuandoACoordenadaNaoENumerica() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200, resultado("abc", "-47.0595640", 16));

        assertThatThrownBy(() -> servico.localizarMunicipio("Campinas", Uf.SP))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    @Test
    void deveFalharComoIndisponivelQuandoACoordenadaEstaForaDaFaixa() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200, resultado("-122.9", "-47.0595640", 16));

        assertThatThrownBy(() -> servico.localizarMunicipio("Campinas", Uf.SP))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    @Test
    void deveFalharComoIndisponivelQuandoFaltaLatitudeNaResposta() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200, "[{\"lon\":\"-47.06\",\"place_rank\":16}]");

        assertThatThrownBy(() -> servico.localizarMunicipio("Campinas", Uf.SP))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    @Test
    void deveFalharComoIndisponivelQuandoFaltaPlaceRankNaResposta() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200, "[{\"lat\":\"-22.9\",\"lon\":\"-47.06\"}]");

        assertThatThrownBy(() -> servico.localizarMunicipio("Campinas", Uf.SP))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    @Test
    void deveFalharComoIndisponivelQuandoONominatimEstaForaDoAr() throws IOException {
        nominatimRespondendo(200, "[]");
        int portaFechada = servidor.getAddress().getPort();
        servidor.stop(0);
        servidor = null;
        NominatimGeocodificador servico = new NominatimGeocodificador("http://127.0.0.1:" + portaFechada, USER_AGENT,
                Duration.ofSeconds(2));

        assertThatThrownBy(() -> servico.localizarMunicipio("Campinas", Uf.SP))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    // ---------- localizarEndereco ----------

    private static final EnderecoParaGeocodificar PAULISTA =
            new EnderecoParaGeocodificar("Avenida Paulista", "1000", "São Paulo", Uf.SP);

    private static String resultadoEndereco(String lat, String lon, int placeRank, String estado) {
        return "[{\"place_id\":1,\"lat\":\"" + lat + "\",\"lon\":\"" + lon + "\",\"place_rank\":" + placeRank
                + ",\"address\":{\"road\":\"Avenida Paulista\",\"state\":\"" + estado + "\"}}]";
    }

    @Test
    void deveLocalizarOEnderecoMontandoNumeroRuaCidadeEUfPorExtenso() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200,
                resultadoEndereco("-23.5614", "-46.6559", 30, "São Paulo"));

        Optional<Coordenadas> coordenadas = servico.localizarEndereco(PAULISTA);

        assertThat(coordenadas).contains(new Coordenadas(-23.5614, -46.6559));
        assertThat(consultaRecebida)
                .contains("street=1000%20Avenida%20Paulista")
                .contains("city=S%C3%A3o%20Paulo")
                .contains("state=S%C3%A3o%20Paulo")
                .contains("countrycodes=br")
                .contains("format=jsonv2")
                .contains("addressdetails=1")
                .contains("limit=1")
                .doesNotContain("postalcode")
                .doesNotContain("featureType");
        assertThat(userAgentRecebido).isEqualTo(USER_AGENT);
    }

    @Test
    void deveCodificarARuaDigitadaInteiraSemAbrirParametroNovo() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200, "[]");

        servico.localizarEndereco(new EnderecoParaGeocodificar("Rua A&limit=40", "1", "Santos", Uf.SP));

        assertThat(consultaRecebida).contains("street=1%20Rua%20A%26limit%3D40").doesNotContain("limit=40");
    }

    @Test
    void deveAceitarRuaOuNumeroDeCasaEComparandoOEstadoSemCaixaENemAcento() throws IOException {
        assertThat(nominatimRespondendo(200, resultadoEndereco("-23.5", "-46.6", 26, "Sao Paulo"))
                .localizarEndereco(PAULISTA)).isPresent();
        servidor.stop(0);
        assertThat(nominatimRespondendo(200, resultadoEndereco("-23.5", "-46.6", 30, "SÃO PAULO"))
                .localizarEndereco(PAULISTA)).isPresent();
    }

    @Test
    void deveDevolverVazioQuandoNaoAchaOEndereco() throws IOException {
        assertThat(nominatimRespondendo(200, "[]").localizarEndereco(PAULISTA)).isEmpty();
    }

    @Test
    void deveDevolverVazioQuandoOResultadoESoBairroOuCidadeENaoRua() throws IOException {
        assertThat(nominatimRespondendo(200, resultadoEndereco("-23.5", "-46.6", 25, "São Paulo"))
                .localizarEndereco(PAULISTA)).isEmpty();
        servidor.stop(0);
        assertThat(nominatimRespondendo(200, resultadoEndereco("-23.5", "-46.6", 16, "São Paulo"))
                .localizarEndereco(PAULISTA)).isEmpty();
    }

    @Test
    void deveDevolverVazioQuandoOResultadoEDeOutraUf() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200,
                resultadoEndereco("-22.9", "-43.2", 30, "Rio de Janeiro"));

        assertThat(servico.localizarEndereco(PAULISTA)).isEmpty();
    }

    @Test
    void deveFalharComoIndisponivelQuandoAhRespostaNaoTrazEstadoNemEndereco() throws IOException {
        assertThatThrownBy(() -> nominatimRespondendo(200,
                "[{\"lat\":\"-23.5\",\"lon\":\"-46.6\",\"place_rank\":30}]").localizarEndereco(PAULISTA))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
        servidor.stop(0);
        assertThatThrownBy(() -> nominatimRespondendo(200,
                "[{\"lat\":\"-23.5\",\"lon\":\"-46.6\",\"place_rank\":30,\"address\":{}}]")
                .localizarEndereco(PAULISTA)).isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    @Test
    void deveFalharComoIndisponivelQuandoFaltaLatitudeOuPlaceRankNaResposta() throws IOException {
        assertThatThrownBy(() -> nominatimRespondendo(200,
                "[{\"lon\":\"-46.6\",\"place_rank\":30,\"address\":{\"state\":\"São Paulo\"}}]")
                .localizarEndereco(PAULISTA)).isInstanceOf(GeocodificacaoIndisponivelException.class);
        servidor.stop(0);
        assertThatThrownBy(() -> nominatimRespondendo(200,
                "[{\"lat\":\"-23.5\",\"lon\":\"-46.6\",\"address\":{\"state\":\"São Paulo\"}}]")
                .localizarEndereco(PAULISTA)).isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    @Test
    void deveFalharComoIndisponivelQuandoACoordenadaDoEnderecoNaoENumericaOuForaDaFaixa() throws IOException {
        assertThatThrownBy(() -> nominatimRespondendo(200, resultadoEndereco("abc", "-46.6", 30, "São Paulo"))
                .localizarEndereco(PAULISTA)).isInstanceOf(GeocodificacaoIndisponivelException.class);
        servidor.stop(0);
        assertThatThrownBy(() -> nominatimRespondendo(200, resultadoEndereco("-123.5", "-46.6", 30, "São Paulo"))
                .localizarEndereco(PAULISTA)).isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    @Test
    void deveFalharComoIndisponivelNoErroHttpDoProvedorEmJsonMalformado() throws IOException {
        assertThatThrownBy(() -> nominatimRespondendo(500, "{\"error\":\"boom\"}").localizarEndereco(PAULISTA))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
        servidor.stop(0);
        assertThatThrownBy(() -> nominatimRespondendo(429, "{\"error\":\"too many requests\"}")
                .localizarEndereco(PAULISTA)).isInstanceOf(GeocodificacaoIndisponivelException.class);
        servidor.stop(0);
        assertThatThrownBy(() -> nominatimRespondendo(200, "isto não é json").localizarEndereco(PAULISTA))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    @Test
    void deveFalharComoIndisponivelQuandoOProvedorDemoraMaisQueOTimeout() throws IOException {
        NominatimGeocodificador servico = nominatimRespondendo(200,
                resultadoEndereco("-23.5", "-46.6", 30, "São Paulo"), Duration.ofSeconds(3),
                Duration.ofMillis(300));

        assertThatThrownBy(() -> servico.localizarEndereco(PAULISTA))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
    }

    @Test
    void deveFalharComoIndisponivelQuandoOProvedorDoEnderecoEstaForaDoAr() throws IOException {
        nominatimRespondendo(200, "[]");
        int portaFechada = servidor.getAddress().getPort();
        servidor.stop(0);
        servidor = null;
        NominatimGeocodificador servico = new NominatimGeocodificador("http://127.0.0.1:" + portaFechada, USER_AGENT,
                Duration.ofSeconds(2));

        assertThatThrownBy(() -> servico.localizarEndereco(PAULISTA))
                .isInstanceOf(GeocodificacaoIndisponivelException.class);
    }
}
