package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.limio.api.comum.portas.EscolhaDoPrestador;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.modulos.perfil.actions.mapper.CotaDoMesMapper;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaDoMes;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;
import com.limio.api.modulos.perfil.records.CotaDoMesResponse;
import com.limio.api.modulos.perfil.records.EscolhaDoMesResponse;

class CotaDoMesMapperTest {

    private final CotaDoMesMapper mapper = new CotaDoMesMapper();

    @Test
    void deveConverterACotaComRenovacaoNoHorarioDeSaoPaulo() {
        UUID anuncioId = UUID.randomUUID();
        Instant servico = Instant.parse("2026-10-30T13:00:00Z");
        Instant escolhidaEm = Instant.parse("2026-10-20T12:00:00Z");
        CotaDoMes cotaDoMes = new CotaDoMes(Plano.FREE, CotaMensal.zerada(null, YearMonth.of(2026, 10), 4),
                List.of(new EscolhaDoPrestador(anuncioId, "Faxina", servico, escolhidaEm, "ESCOLHIDA")));

        CotaDoMesResponse resposta = mapper.toResponse(cotaDoMes);

        assertThat(resposta).isEqualTo(new CotaDoMesResponse(Plano.FREE, "2026-10", 0, 4, false,
                OffsetDateTime.parse("2026-11-01T00:00:00-03:00"),
                List.of(new EscolhaDoMesResponse(anuncioId, "Faxina", servico, escolhidaEm, "ESCOLHIDA"))));
    }

    @Test
    void goldSaiSemTeto() {
        CotaDoMes cotaDoMes = new CotaDoMes(Plano.GOLD, CotaMensal.zerada(null, YearMonth.of(2026, 10), 4), List.of());

        assertThat(mapper.toResponse(cotaDoMes).tetoAplicado()).isNull();
    }
}
