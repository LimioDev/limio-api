package com.limio.api.modulos.notificacao.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.limio.api.modulos.notificacao.actions.service.PushExpoService;
import com.limio.api.modulos.notificacao.actions.service.PushExpoService.ResultadoPush;

/** Contrato com o Expo Push Service, sem rede: {@code MockRestServiceServer} no lugar do Expo. */
class PushExpoServiceTest {

    private static final String URL = "https://expo.test/push/send";
    private static final Map<String, String> DADOS = Map.of("tipo", "NOVA_CANDIDATURA");

    private RestClient.Builder builder;
    private MockRestServiceServer expo;

    @BeforeEach
    void preparar() {
        builder = RestClient.builder();
        expo = MockRestServiceServer.bindTo(builder).build();
    }

    private PushExpoService servico(boolean habilitado) {
        return new PushExpoService(builder, URL, "token-de-acesso", habilitado);
    }

    private static List<String> tokens(int quantos) {
        return IntStream.range(0, quantos).mapToObj(i -> "ExponentPushToken[" + i + "]").toList();
    }

    /** Um ticket {@code ok} por mensagem, menos os índices em {@code invalidos}, que voltam DeviceNotRegistered. */
    private static String tickets(int quantos, List<Integer> invalidos) {
        return IntStream.range(0, quantos)
                .mapToObj(i -> invalidos.contains(i)
                        ? "{\"status\":\"error\",\"message\":\"não registrado\","
                                + "\"details\":{\"error\":\"DeviceNotRegistered\"}}"
                        : "{\"status\":\"ok\",\"id\":\"ticket-" + i + "\"}")
                .collect(Collectors.joining(",", "{\"data\":[", "]}"));
    }

    @Test
    void dadoTicketDeviceNotRegistered_quandoEnvia_entaoDevolveOTokenPraApagar() {
        expo.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-de-acesso"))
                .andExpect(jsonPath("$[0].to").value("ExponentPushToken[0]"))
                .andExpect(jsonPath("$[0].title").value("Novo candidato"))
                .andExpect(jsonPath("$[0].data.tipo").value("NOVA_CANDIDATURA"))
                .andRespond(withSuccess(tickets(2, List.of(1)), MediaType.APPLICATION_JSON));

        ResultadoPush resultado = servico(true).enviar(tokens(2), "Novo candidato", "corpo", DADOS);

        expo.verify();
        assertThat(resultado.tentou()).isTrue();
        assertThat(resultado.tokensInvalidos()).containsExactly("ExponentPushToken[1]");
        assertThat(resultado.falha()).isEmpty();
    }

    @Test
    void dadoErroQueNaoEDeToken_quandoEnvia_entaoNaoApagaNada() {
        expo.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"data\":[{\"status\":\"error\",\"message\":\"muito grande\","
                        + "\"details\":{\"error\":\"MessageTooBig\"}}]}",
                MediaType.APPLICATION_JSON));

        ResultadoPush resultado = servico(true).enviar(tokens(1), "Novo candidato", "corpo", DADOS);

        assertThat(resultado.tokensInvalidos()).isEmpty();
        assertThat(resultado.falha()).isEmpty();
    }

    @Test
    void dadoMaisDe100Aparelhos_quandoEnvia_entaoQuebraEmLotesDe100() {
        expo.expect(requestTo(URL))
                .andExpect(jsonPath("$", Matchers.hasSize(100)))
                .andRespond(withSuccess(tickets(100, List.of()), MediaType.APPLICATION_JSON));
        expo.expect(requestTo(URL))
                .andExpect(jsonPath("$", Matchers.hasSize(50)))
                .andExpect(jsonPath("$[0].to").value("ExponentPushToken[100]"))
                .andRespond(withSuccess(tickets(50, List.of(0)), MediaType.APPLICATION_JSON));

        ResultadoPush resultado = servico(true).enviar(tokens(150), "Novo candidato", "corpo", DADOS);

        expo.verify();
        assertThat(resultado.tokensInvalidos()).containsExactly("ExponentPushToken[100]");
        assertThat(resultado.falha()).isEmpty();
    }

    @Test
    void dadoSegundoLoteFalha_quandoEnvia_entaoGuardaOsInvalidosDoPrimeiroEInformaAFalha() {
        expo.expect(requestTo(URL))
                .andRespond(withSuccess(tickets(100, List.of(3)), MediaType.APPLICATION_JSON));
        expo.expect(requestTo(URL)).andRespond(withServerError());

        ResultadoPush resultado = servico(true).enviar(tokens(150), "Novo candidato", "corpo", DADOS);

        expo.verify();
        assertThat(resultado.tentou()).isTrue();
        assertThat(resultado.tokensInvalidos()).containsExactly("ExponentPushToken[3]");
        assertThat(resultado.falha()).hasValueSatisfying(falha -> assertThat(falha).startsWith("lote 2 de 2: "));
    }

    @Test
    void dadoRespostaSemTickets_quandoEnvia_entaoInformaAFalha() {
        expo.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ResultadoPush resultado = servico(true).enviar(tokens(1), "Novo candidato", "corpo", DADOS);

        assertThat(resultado.falha()).hasValueSatisfying(falha -> assertThat(falha).contains("sem tickets"));
    }

    @Test
    void dadoEnvioDesligado_quandoEnvia_entaoNaoChamaOExpoEInformaQueNaoTentou() {
        ResultadoPush resultado = servico(false).enviar(tokens(2), "Novo candidato", "corpo", DADOS);

        expo.verify();
        assertThat(resultado.tentou()).isFalse();
        assertThat(resultado.tokensInvalidos()).isEmpty();
    }
}
