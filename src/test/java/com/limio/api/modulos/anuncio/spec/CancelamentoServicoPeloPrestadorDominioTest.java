package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.enums.CausaEncerramento;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/** Transições de {@code Anuncio} e {@code Aplicacao} no cancelamento do serviço pelo Prestador (ETI-96). */
class CancelamentoServicoPeloPrestadorDominioTest {

    private static final Instant AGORA = Instant.parse("2026-11-18T15:00:00Z");

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO" })
    void dadoServicoEmAberto_quandoPrestadorCancela_entaoEncerradoComDataEMotivoSemTipo(StatusAnuncio status) {
        Anuncio anuncio = Anuncio.builder().status(status).dataHoraPretendida(AGORA.plusSeconds(3600)).build();

        anuncio.cancelarServicoPeloPrestador(AGORA);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getEncerradoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getMotivoEncerramento()).isEqualTo("Serviço cancelado pelo Prestador");
        assertThat(anuncio.getCausaEncerramento()).isEqualTo(CausaEncerramento.CANCELAMENTO);
        assertThat(anuncio.getTipoCancelamento()).isNull();
    }

    @Test
    void dadoServicoEmAberto_quandoPrestadorCancelaSemAntecedencia_entaoNaoHaCarencia() {
        Anuncio anuncio = Anuncio.builder().status(StatusAnuncio.PUBLICADO).dataHoraPretendida(AGORA.plusSeconds(60))
                .build();

        anuncio.cancelarServicoPeloPrestador(AGORA);

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getTipoCancelamento()).isNull();
    }

    @Test
    void dadoCandidaturaEscolhida_quandoPrestadorCancela_entaoEncerradaSemTocarNaCota() {
        Aplicacao escolhida = Aplicacao.builder().status(StatusAplicacao.ESCOLHIDA).cotaConsumida(true).build();

        escolhida.encerrarPorCancelamentoDoServicoPeloPrestador(AGORA);

        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(escolhida.getEncerradaEm()).isEqualTo(AGORA);
        assertThat(escolhida.getMotivoEncerramento()).isEqualTo("Serviço cancelado pelo Prestador");
        assertThat(escolhida.isCotaConsumida()).isTrue();
        assertThat(escolhida.getCotaEstornadaEm()).isNull();
    }
}
