package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.auth.actions.helper.LimiteTentativasLogin;

class LimiteTentativasLoginTest {

    private static final Instant AGORA = Instant.parse("2026-10-07T12:00:00Z");

    private final LimiteTentativasLogin limite =
            new LimiteTentativasLogin(5, Duration.ofMinutes(15), Duration.ofMinutes(15));

    /** Falhas de "n minutos atrás", da mais recente pra mais antiga. */
    private static List<Instant> falhasHaMinutos(int... minutos) {
        return Arrays.stream(minutos).mapToObj(m -> AGORA.minus(Duration.ofMinutes(m))).toList();
    }

    @Test
    void naoBloqueiaComMenosFalhasQueOLimite() {
        assertThat(limite.estaBloqueado(falhasHaMinutos(0, 1, 2, 3), AGORA)).isFalse();
    }

    @Test
    void bloqueiaQuandoCincoFalhasCabemNaJanela() {
        assertThat(limite.estaBloqueado(falhasHaMinutos(0, 1, 2, 3, 4), AGORA)).isTrue();
    }

    @Test
    void naoBloqueiaQuandoAsCincoFalhasNaoCabemNaJanela() {
        assertThat(limite.estaBloqueado(falhasHaMinutos(0, 1, 2, 3, 16), AGORA)).isFalse();
    }

    @Test
    void bloqueioDuraQuinzeMinutosContadosDaUltimaFalha() {
        assertThat(limite.estaBloqueado(falhasHaMinutos(14, 15, 16, 17, 18), AGORA)).isTrue();
        assertThat(limite.estaBloqueado(falhasHaMinutos(15, 16, 17, 18, 19), AGORA)).isFalse();
    }

    @Test
    void recusaConfiguracaoSemNenhumaFalhaPermitida() {
        assertThatThrownBy(() -> new LimiteTentativasLogin(0, Duration.ofMinutes(15), Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
