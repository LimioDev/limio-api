package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.anuncio.CriterioDoHistorico;

class CriterioDoHistoricoTest {

    private static final Instant T = Instant.parse("2026-10-01T03:00:00Z");

    @Test
    void dadoInicioDepoisDoFim_quandoCria_entaoRecusa() {
        assertThatThrownBy(() -> new CriterioDoHistorico(null, T.plusSeconds(1), T))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dadoInicioIgualAoFim_quandoCria_entaoAceita() {
        assertThatCode(() -> new CriterioDoHistorico(null, T, T)).doesNotThrowAnyException();
    }

    @Test
    void dadoSemFiltros_quandoCria_entaoTudoNulo() {
        CriterioDoHistorico criterio = new CriterioDoHistorico(null, null, null);

        assertThat(criterio.status()).isNull();
        assertThat(criterio.de()).isNull();
        assertThat(criterio.ate()).isNull();
    }
}
