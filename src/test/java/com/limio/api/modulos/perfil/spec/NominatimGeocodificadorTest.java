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
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/search", troca -> {
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
                Duration.ofSeconds(2));
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
}
