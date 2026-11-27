package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.limio.api.modulos.anuncio.actions.helper.PeriodoHistoricoHelper;
import com.limio.api.modulos.anuncio.actions.helper.PeriodoHistoricoHelper.Periodo;

/** Período do histórico (ETI-71): dias de Brasília (UTC-3, sem horário de verão) viram instantes. */
class PeriodoHistoricoHelperTest {

    @Test
    void dadoInicioEFim_quandoConverte_entaoDoInicioDoPrimeiroDiaAoInicioDoDiaSeguinteAoUltimo() {
        Periodo periodo = PeriodoHistoricoHelper.de(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(periodo.de()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(periodo.ate()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
    }

    @Test
    void dadoMesmoDiaNasDuasPontas_quandoConverte_entaoCobreOsVinteEQuatroHorasDoDia() {
        Periodo periodo = PeriodoHistoricoHelper.de(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5));

        assertThat(periodo.de()).isEqualTo(Instant.parse("2026-10-05T03:00:00Z"));
        assertThat(periodo.ate()).isEqualTo(Instant.parse("2026-10-06T03:00:00Z"));
    }

    @Test
    void dadoFimEmViradaDeMes_quandoConverte_entaoLimiteVaiParaOPrimeiroDoMesSeguinte() {
        Periodo periodo = PeriodoHistoricoHelper.de(null, LocalDate.of(2026, 12, 31));

        assertThat(periodo.ate()).isEqualTo(Instant.parse("2027-01-01T03:00:00Z"));
    }

    @Test
    void dadoSoInicio_quandoConverte_entaoSemLimiteSuperior() {
        Periodo periodo = PeriodoHistoricoHelper.de(LocalDate.of(2026, 10, 1), null);

        assertThat(periodo.de()).isNotNull();
        assertThat(periodo.ate()).isNull();
    }

    @Test
    void dadoSemDatas_quandoConverte_entaoSemLimites() {
        Periodo periodo = PeriodoHistoricoHelper.de(null, null);

        assertThat(periodo.de()).isNull();
        assertThat(periodo.ate()).isNull();
    }
}
