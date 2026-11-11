package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.limio.api.comum.eventos.enums.TipoCancelamento;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/** Transições de {@code Anuncio} e {@code Aplicacao} no cancelamento do serviço combinado (ETI-94). */
class CancelamentoServicoDominioTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T15:00:00Z");
    private static final Duration VINTE_E_QUATRO_HORAS = Duration.ofHours(24);

    private static Anuncio anuncioParaDaquiA(Duration quanto) {
        return Anuncio.builder().status(StatusAnuncio.PUBLICADO).dataHoraPretendida(AGORA.plus(quanto)).build();
    }

    @Test
    void antecedenciaDe24HorasContaQuandoFaltamExatamente24Horas() {
        assertThat(anuncioParaDaquiA(VINTE_E_QUATRO_HORAS).temAntecedenciaDe(VINTE_E_QUATRO_HORAS, AGORA)).isTrue();
        assertThat(anuncioParaDaquiA(VINTE_E_QUATRO_HORAS.plusMillis(1)).temAntecedenciaDe(VINTE_E_QUATRO_HORAS,
                AGORA)).isTrue();
    }

    @Test
    void antecedenciaDe24HorasNaoContaQuandoFaltaMenosOuADataJaPassou() {
        assertThat(anuncioParaDaquiA(VINTE_E_QUATRO_HORAS.minusMillis(1)).temAntecedenciaDe(VINTE_E_QUATRO_HORAS,
                AGORA)).isFalse();
        assertThat(anuncioParaDaquiA(Duration.ZERO).temAntecedenciaDe(VINTE_E_QUATRO_HORAS, AGORA)).isFalse();
        assertThat(anuncioParaDaquiA(Duration.ofHours(-1)).temAntecedenciaDe(VINTE_E_QUATRO_HORAS, AGORA)).isFalse();
    }

    @Test
    void cancelarServicoNormalEncerraORegistraOTipoEOMotivo() {
        Anuncio anuncio = anuncioParaDaquiA(Duration.ofDays(3));

        anuncio.cancelarServicoCombinado(AGORA, TipoCancelamento.NORMAL);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getEncerradoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getTipoCancelamento()).isEqualTo(TipoCancelamento.NORMAL);
        assertThat(anuncio.getMotivoEncerramento()).isEqualTo("Serviço cancelado pelo Empregador");
    }

    @Test
    void cancelarServicoTardioRegistraOMotivoDaCarencia() {
        Anuncio anuncio = anuncioParaDaquiA(Duration.ofDays(3));

        anuncio.cancelarServicoCombinado(AGORA, TipoCancelamento.TARDIO);

        assertThat(anuncio.getTipoCancelamento()).isEqualTo(TipoCancelamento.TARDIO);
        assertThat(anuncio.getMotivoEncerramento())
                .isEqualTo("Serviço cancelado pelo Empregador na carência pós-reagendamento");
    }

    @Test
    void cancelarAntesDaEscolhaNaoPreencheOTipoDeCancelamento() {
        Anuncio anuncio = anuncioParaDaquiA(Duration.ofDays(3));

        anuncio.cancelarPeloEmpregador(AGORA);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getTipoCancelamento()).isNull();
    }

    @Test
    void aplicacaoEscolhidaEncerradaPeloCancelamentoDoServico() {
        Aplicacao aplicacao = Aplicacao.builder().status(StatusAplicacao.ESCOLHIDA).escolhidaEm(AGORA.minusSeconds(60))
                .cotaConsumida(true).build();

        aplicacao.encerrarPorCancelamentoDoServico(AGORA);

        assertThat(aplicacao.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(aplicacao.getEncerradaEm()).isEqualTo(AGORA);
        assertThat(aplicacao.getMotivoEncerramento()).isEqualTo("Serviço cancelado pelo Empregador");
        assertThat(aplicacao.getEscolhidaEm()).as("escolhidaEm é permanente (ETI-86)").isEqualTo(AGORA.minusSeconds(60));
        assertThat(aplicacao.getCotaEstornadaEm()).isNull();
    }

    @Test
    void registrarEstornoGravaQuandoACotaFoiDevolvida() {
        Aplicacao aplicacao = Aplicacao.builder().status(StatusAplicacao.ESCOLHIDA).cotaConsumida(true).build();

        aplicacao.registrarEstornoDeCota(AGORA);

        assertThat(aplicacao.getCotaEstornadaEm()).isEqualTo(AGORA);
    }
}
