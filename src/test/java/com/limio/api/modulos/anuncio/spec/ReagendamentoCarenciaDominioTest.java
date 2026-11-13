package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;

/** Carência pós-reagendamento (RB36, ETI-94): a conta de tempo vive no domínio. */
class ReagendamentoCarenciaDominioTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T15:00:00Z");
    private static final Duration CARENCIA = Duration.ofHours(2);

    private static Reagendamento respondidoHa(Duration quanto) {
        return Reagendamento.builder().status(StatusReagendamento.ACEITO).respondidoEm(AGORA.minus(quanto)).build();
    }

    @Test
    void estaNaCarenciaQuandoRespondeuHaMenosQueACarencia() {
        assertThat(respondidoHa(Duration.ZERO).estaNaCarencia(CARENCIA, AGORA)).isTrue();
        assertThat(respondidoHa(CARENCIA.minusMillis(1)).estaNaCarencia(CARENCIA, AGORA)).isTrue();
    }

    @Test
    void naoEstaNaCarenciaQuandoPassouExatamenteACarenciaOuMais() {
        assertThat(respondidoHa(CARENCIA).estaNaCarencia(CARENCIA, AGORA)).isFalse();
        assertThat(respondidoHa(CARENCIA.plusMillis(1)).estaNaCarencia(CARENCIA, AGORA)).isFalse();
    }

    @Test
    void semRespostaRegistradaNaoHaCarencia() {
        Reagendamento semResposta = Reagendamento.builder().status(StatusReagendamento.ACEITO).build();

        assertThat(semResposta.estaNaCarencia(CARENCIA, AGORA)).isFalse();
    }
}
