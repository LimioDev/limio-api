package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/** Regras de domínio da conclusão por silêncio (ETI-98), sem Spring nem banco. */
class ConclusaoPorSilencioDominioTest {

    private static final Instant AGORA = Instant.parse("2026-11-25T12:00:00Z");
    private static final Duration POS_AGENDA = Duration.ofHours(24);
    private static final Duration VALIDACAO_PADRAO = Duration.ofHours(24);

    private static Anuncio agendado(StatusAnuncio status, Instant dataHoraPretendida) {
        return Anuncio.builder().status(status).dataHoraPretendida(dataHoraPretendida).build();
    }

    private static Anuncio comCheckout(Instant checkoutEm, Integer prazoValidacaoHoras) {
        return Anuncio.builder()
                .status(StatusAnuncio.AGUARDANDO_VALIDACAO)
                // Data antiga de propósito: depois do check-out ela não conta mais.
                .dataHoraPretendida(AGORA.minus(Duration.ofDays(10)))
                .checkoutEm(checkoutEm)
                .prazoValidacaoHoras(prazoValidacaoHoras)
                .build();
    }

    private static boolean permite(Anuncio anuncio) {
        return anuncio.permiteConclusaoPorSilencio(AGORA, POS_AGENDA, VALIDACAO_PADRAO);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" })
    void dadaDataVigenteVencidaAlemDoPrazo_quandoPergunta_entaoSim(StatusAnuncio status) {
        assertThat(permite(agendado(status, AGORA.minus(POS_AGENDA).minusSeconds(1)))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" })
    void dadoPrazoPosAgendaVencendoAgora_quandoPergunta_entaoSim(StatusAnuncio status) {
        assertThat(permite(agendado(status, AGORA.minus(POS_AGENDA)))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" })
    void dadoServicoAindaDentroDoPrazo_quandoPergunta_entaoNao(StatusAnuncio status) {
        assertThat(permite(agendado(status, AGORA.minus(POS_AGENDA).plusSeconds(1)))).isFalse();
    }

    @Test
    void dadaDataReagendadaNoFuturo_quandoPergunta_entaoVaiPelaNovaData() {
        Anuncio anuncio = agendado(StatusAnuncio.PUBLICADO, AGORA.minus(Duration.ofDays(3)));

        anuncio.reagendar(AGORA.plus(Duration.ofDays(1)));

        assertThat(permite(anuncio)).isFalse();
    }

    @Test
    void dadoCheckoutComPrazoVencido_quandoPergunta_entaoSim() {
        assertThat(permite(comCheckout(AGORA.minus(Duration.ofHours(24)), 24))).isTrue();
    }

    @Test
    void dadoCheckoutAindaDentroDoPrazo_quandoPergunta_entaoNao() {
        assertThat(permite(comCheckout(AGORA.minus(Duration.ofHours(24)).plusSeconds(1), 24))).isFalse();
    }

    @Test
    void dadoPrazoDeValidacaoProprioDoAnuncio_quandoPergunta_entaoEleVenceOPadrao() {
        assertThat(permite(comCheckout(AGORA.minus(Duration.ofHours(30)), 48))).isFalse();
        assertThat(permite(comCheckout(AGORA.minus(Duration.ofHours(2)), 1))).isTrue();
    }

    @Test
    void dadoAnuncioSemPrazoDeValidacao_quandoPergunta_entaoUsaOPadrao() {
        assertThat(permite(comCheckout(AGORA.minus(VALIDACAO_PADRAO), null))).isTrue();
        assertThat(permite(comCheckout(AGORA.minus(VALIDACAO_PADRAO).plusSeconds(1), null))).isFalse();
    }

    @Test
    void dadoAguardandoValidacaoSemCheckoutEm_quandoPergunta_entaoNao() {
        assertThat(permite(comCheckout(null, 24))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "CONCLUIDO", "ENCERRADO" })
    void dadoEstadoFinal_quandoPergunta_entaoNao(StatusAnuncio status) {
        assertThat(permite(agendado(status, AGORA.minus(Duration.ofDays(10))))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO", "AGUARDANDO_VALIDACAO" })
    void dadoServicoEmAberto_quandoConcluiPorSilencio_entaoGravaValidacaoTacitaEEncerramento(StatusAnuncio status) {
        Instant pretendida = AGORA.minus(Duration.ofDays(2));
        Anuncio anuncio = agendado(status, pretendida);

        anuncio.concluirPorSilencio(AGORA);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.CONCLUIDO);
        assertThat(anuncio.getValidadoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getEncerradoEm()).isEqualTo(AGORA);
        assertThat(anuncio.concluidoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getMotivoEncerramento()).isNull();
        assertThat(anuncio.getDataHoraPretendida()).isEqualTo(pretendida);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "CONCLUIDO", "ENCERRADO" })
    void dadoEstadoFinal_quandoConcluiPorSilencio_entaoRecusaSemAlterar(StatusAnuncio status) {
        Anuncio anuncio = agendado(status, AGORA.minus(Duration.ofDays(2)));

        assertThatThrownBy(() -> anuncio.concluirPorSilencio(AGORA)).isInstanceOf(IllegalStateException.class);

        assertThat(anuncio.getStatus()).isEqualTo(status);
        assertThat(anuncio.getValidadoEm()).isNull();
        assertThat(anuncio.getEncerradoEm()).isNull();
    }
}
