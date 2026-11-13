package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/** Regras de domínio do check-out (ETI-90), sem Spring nem banco. */
class CheckoutDominioTest {

    private static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");

    private static Anuncio anuncio(StatusAnuncio status, Integer prazoHoras) {
        return Anuncio.builder().status(status).prazoValidacaoHoras(prazoHoras).build();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" })
    void dadoAnuncioPublicadoOuEmAndamento_quandoPerguntaSePermiteCheckout_entaoSim(StatusAnuncio status) {
        assertThat(anuncio(status, 24).servicoEmAberto()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "AGUARDANDO_VALIDACAO", "CONCLUIDO", "ENCERRADO" })
    void dadoAnuncioEmOutroEstado_quandoPerguntaSePermiteCheckout_entaoNao(StatusAnuncio status) {
        assertThat(anuncio(status, 24).servicoEmAberto()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" })
    void dadoCheckoutPermitido_quandoRegistra_entaoAguardaValidacaoSemConcluir(StatusAnuncio status) {
        Anuncio anuncio = anuncio(status, 24);

        anuncio.registrarCheckout(AGORA, null);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.AGUARDANDO_VALIDACAO);
        assertThat(anuncio.getCheckoutEm()).isEqualTo(AGORA);
        assertThat(anuncio.getValidadoEm()).isNull();
    }

    @Test
    void dadaObservacao_quandoRegistraCheckout_entaoGuardaSemEspacosNasPontas() {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO, 24);

        anuncio.registrarCheckout(AGORA, "  Serviço feito, sobrou entulho no quintal.  ");

        assertThat(anuncio.getObservacaoCheckout()).isEqualTo("Serviço feito, sobrou entulho no quintal.");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = { "   " })
    void dadaObservacaoVazia_quandoRegistraCheckout_entaoNaoGuarda(String observacao) {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO, 24);

        anuncio.registrarCheckout(AGORA, observacao);

        assertThat(anuncio.getObservacaoCheckout()).isNull();
    }

    @Test
    void dadoCheckoutRegistrado_quandoCalculaPrazo_entaoSomaHorasGravadasNoAnuncio() {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO, 24);
        anuncio.registrarCheckout(AGORA, null);

        assertThat(anuncio.prazoDeValidacao()).contains(Instant.parse("2026-11-08T12:00:00Z"));
    }

    @Test
    void dadoPrazoDiferenteDe24h_quandoCalculaPrazo_entaoUsaOGravado() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, 48);
        anuncio.registrarCheckout(AGORA, null);

        assertThat(anuncio.prazoDeValidacao()).contains(Instant.parse("2026-11-09T12:00:00Z"));
    }

    @Test
    void dadoAnuncioSemCheckout_quandoCalculaPrazo_entaoVazio() {
        assertThat(anuncio(StatusAnuncio.PUBLICADO, 24).prazoDeValidacao()).isEmpty();
    }

    @Test
    void dadoAnuncioSemPrazoGravado_quandoCalculaPrazo_entaoVazio() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, null);
        anuncio.registrarCheckout(AGORA, null);

        assertThat(anuncio.prazoDeValidacao()).isEmpty();
    }

    @Test
    void dadaCandidaturaEscolhida_quandoRegistraCheckout_entaoAguardaValidacaoEContinuaEscolhida() {
        Aplicacao aplicacao = Aplicacao.builder().anuncioId(UUID.randomUUID()).prestadorId(UUID.randomUUID())
                .status(StatusAplicacao.ESCOLHIDA).build();

        aplicacao.registrarCheckout();

        assertThat(aplicacao.getStatus()).isEqualTo(StatusAplicacao.AGUARDANDO_VALIDACAO);
        assertThat(aplicacao.isEscolhida()).isTrue();
    }
}
