package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.anuncio.enums.AbaCandidatura;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

class AbaCandidaturaTest {

    @Test
    void quandoListaStatusDeCadaAba_entaoBateComOCard() {
        assertThat(AbaCandidatura.PENDENTES.status()).containsExactly(StatusAplicacao.ENVIADA);
        assertThat(AbaCandidatura.COMBINADAS.status())
                .containsExactlyInAnyOrder(StatusAplicacao.ESCOLHIDA, StatusAplicacao.AGUARDANDO_VALIDACAO);
        assertThat(AbaCandidatura.HISTORICO.status()).containsExactlyInAnyOrder(StatusAplicacao.RECUSADA,
                StatusAplicacao.CONCLUIDA, StatusAplicacao.ENCERRADA);
    }

    @Test
    void quandoJuntaTodasAsAbas_entaoCadaStatusApareceUmaVezSo() {
        assertThat(Arrays.stream(AbaCandidatura.values()).flatMap(aba -> aba.status().stream()))
                .containsExactlyInAnyOrder(StatusAplicacao.values());
    }
}
