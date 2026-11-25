package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.limio.api.modulos.anuncio.enums.CausaEncerramento;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusHistorico;

/** Classificação do histórico (ETI-71): status do anúncio + causa do encerramento, sem Spring nem banco. */
class StatusHistoricoTest {

    @Test
    void dadoConcluido_quandoClassifica_entaoConcluido() {
        assertThat(StatusHistorico.de(StatusAnuncio.CONCLUIDO, null)).contains(StatusHistorico.CONCLUIDO);
    }

    @Test
    void dadoEncerradoPorCancelamento_quandoClassifica_entaoCancelado() {
        assertThat(StatusHistorico.de(StatusAnuncio.ENCERRADO, CausaEncerramento.CANCELAMENTO))
                .contains(StatusHistorico.CANCELADO);
    }

    @Test
    void dadoEncerradoPorExpiracao_quandoClassifica_entaoExpirado() {
        assertThat(StatusHistorico.de(StatusAnuncio.ENCERRADO, CausaEncerramento.EXPIRACAO))
                .contains(StatusHistorico.EXPIRADO);
    }

    @Test
    void dadoEncerradoSemCausa_quandoClassifica_entaoVazio() {
        assertThat(StatusHistorico.de(StatusAnuncio.ENCERRADO, null)).isEqualTo(Optional.empty());
    }

    @ParameterizedTest
    @EnumSource(value = StatusAnuncio.class, names = { "PUBLICADO", "EM_ANDAMENTO", "AGUARDANDO_VALIDACAO" })
    void dadoAnuncioAindaAberto_quandoClassifica_entaoNaoEHistorico(StatusAnuncio status) {
        assertThat(StatusHistorico.de(status, null)).isEmpty();
    }

    @Test
    void dadoSemFiltro_quandoTraduzParaConsulta_entaoTodosOsStatusFinaisECausas() {
        assertThat(StatusHistorico.statusAceitos(null))
                .containsExactly(StatusAnuncio.CONCLUIDO, StatusAnuncio.ENCERRADO);
        assertThat(StatusHistorico.causasAceitas(null)).containsExactly(CausaEncerramento.values());
    }

    @ParameterizedTest
    @EnumSource(StatusHistorico.class)
    void dadoFiltro_quandoTraduzParaConsulta_entaoSoAceitaOQueClassificaDeVoltaNele(StatusHistorico filtro) {
        assertThat(StatusHistorico.statusAceitos(filtro)).isNotEmpty();
        assertThat(StatusHistorico.causasAceitas(filtro)).isNotEmpty();
        for (StatusAnuncio status : StatusHistorico.statusAceitos(filtro)) {
            for (CausaEncerramento causa : StatusHistorico.causasAceitas(filtro)) {
                assertThat(StatusHistorico.de(status, causa)).contains(filtro);
            }
        }
    }
}
