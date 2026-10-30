package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;

class CotaMensalTest {

    private static final UUID PERFIL_ID = UUID.randomUUID();

    private static CotaMensal cota(YearMonth mes, int consumidas, int teto) {
        CotaMensal cota = CotaMensal.zerada(PERFIL_ID, mes, teto);
        // a contagem só sobe pela escolha (ETI-69/ETI-70), que ainda não existe
        ReflectionTestUtils.setField(cota, "escolhasConsumidas", consumidas);
        return cota;
    }

    @Test
    void mesDaCotaSegueOCalendarioDeSaoPauloENaoOUtc() {
        // 31/10 23:59 em São Paulo já é 01/11 em UTC
        assertThat(CotaMensal.mesDe(Instant.parse("2026-11-01T02:59:59Z"))).isEqualTo(YearMonth.of(2026, 10));
        assertThat(CotaMensal.mesDe(Instant.parse("2026-11-01T03:00:00Z"))).isEqualTo(YearMonth.of(2026, 11));
    }

    @Test
    void comecaNoDia1ERenovaNoDia1DoMesSeguinteAMeiaNoiteDeSaoPaulo() {
        CotaMensal outubro = CotaMensal.zerada(PERFIL_ID, YearMonth.of(2026, 10), 4);

        assertThat(outubro.inicio()).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
        assertThat(outubro.renovaEm()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
    }

    @Test
    void dezembroRenovaEmJaneiroDoAnoSeguinte() {
        assertThat(CotaMensal.zerada(PERFIL_ID, YearMonth.of(2026, 12), 4).renovaEm())
                .isEqualTo(Instant.parse("2027-01-01T03:00:00Z"));
    }

    @Test
    void cotaZeradaNaoTemEscolhaConsumida() {
        CotaMensal cota = CotaMensal.zerada(null, YearMonth.of(2026, 10), 4);

        assertThat(cota.getEscolhasConsumidas()).isZero();
        assertThat(cota.getTetoAplicado()).isEqualTo(4);
        assertThat(cota.isEsgotada()).isFalse();
    }

    @Test
    void esgotaQuandoAsEscolhasChegamAoTeto() {
        assertThat(cota(YearMonth.of(2026, 10), 3, 4).isEsgotada()).isFalse();
        assertThat(cota(YearMonth.of(2026, 10), 4, 4).isEsgotada()).isTrue();
    }
}
