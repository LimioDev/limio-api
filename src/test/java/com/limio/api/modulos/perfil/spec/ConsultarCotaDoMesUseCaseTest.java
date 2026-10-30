package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.portas.ConsultaEscolhasDoPrestador;
import com.limio.api.comum.portas.ConsultaPlanoPrestador;
import com.limio.api.comum.portas.EscolhaDoPrestador;
import com.limio.api.comum.portas.enums.Plano;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.perfil.actions.usecase.ConsultarCotaDoMesUseCase;
import com.limio.api.modulos.perfil.excecao.SomentePrestadorConsultaCotaException;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaDoMes;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensalService;

@ExtendWith(MockitoExtension.class)
class ConsultarCotaDoMesUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID PERFIL_ID = UUID.randomUUID();
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);
    private static final Instant INICIO_OUTUBRO = Instant.parse("2026-10-01T03:00:00Z");
    private static final Instant INICIO_NOVEMBRO = Instant.parse("2026-11-01T03:00:00Z");

    @Mock
    private PerfilPrestadorService perfilPrestadorService;

    @Mock
    private CotaMensalService cotaMensalService;

    @Mock
    private ConsultaPlanoPrestador consultaPlanoPrestador;

    @Mock
    private ConsultaEscolhasDoPrestador consultaEscolhasDoPrestador;

    private ConsultarCotaDoMesUseCase useCase(Instant agora) {
        return new ConsultarCotaDoMesUseCase(perfilPrestadorService, cotaMensalService, consultaPlanoPrestador,
                consultaEscolhasDoPrestador, Clock.fixed(agora, ZoneOffset.UTC), 4);
    }

    private static CotaMensal cota(YearMonth mes, int consumidas, int teto) {
        CotaMensal cota = CotaMensal.zerada(PERFIL_ID, mes, teto);
        ReflectionTestUtils.setField(cota, "escolhasConsumidas", consumidas);
        return cota;
    }

    @Test
    void deveDevolverACotaGravadaDoMesComAsEscolhasDoPeriodo() {
        CotaMensal outubro = cota(OUTUBRO, 2, 4);
        EscolhaDoPrestador escolha = new EscolhaDoPrestador(UUID.randomUUID(), "Faxina", INICIO_OUTUBRO,
                INICIO_OUTUBRO, "ESCOLHIDA");
        when(perfilPrestadorService.buscarIdDoUsuario(USUARIO_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, OUTUBRO)).thenReturn(Optional.of(outubro));
        when(consultaPlanoPrestador.planoAtual(USUARIO_ID)).thenReturn(Plano.FREE);
        when(consultaEscolhasDoPrestador.listarEscolhidasEntre(USUARIO_ID, INICIO_OUTUBRO, INICIO_NOVEMBRO))
                .thenReturn(List.of(escolha));

        CotaDoMes cotaDoMes = useCase(Instant.parse("2026-10-29T15:00:00Z")).executar(USUARIO_ID,
                PapelUsuario.PRESTADOR);

        assertThat(cotaDoMes.plano()).isEqualTo(Plano.FREE);
        assertThat(cotaDoMes.cota()).isSameAs(outubro);
        assertThat(cotaDoMes.teto()).isEqualTo(4);
        assertThat(cotaDoMes.isEsgotada()).isFalse();
        assertThat(cotaDoMes.escolhas()).containsExactly(escolha);
    }

    @Test
    void mesSemLinhaVemZeradoComOTetoFreeEmVigor() {
        when(perfilPrestadorService.buscarIdDoUsuario(USUARIO_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, OUTUBRO)).thenReturn(Optional.empty());
        when(consultaPlanoPrestador.planoAtual(USUARIO_ID)).thenReturn(Plano.FREE);

        CotaDoMes cotaDoMes = useCase(Instant.parse("2026-10-29T15:00:00Z")).executar(USUARIO_ID,
                PapelUsuario.PRESTADOR);

        assertThat(cotaDoMes.cota().getAnoMes()).isEqualTo(OUTUBRO);
        assertThat(cotaDoMes.cota().getEscolhasConsumidas()).isZero();
        assertThat(cotaDoMes.teto()).isEqualTo(4);
        verify(cotaMensalService, never()).salvar(any());
    }

    @Test
    void prestadorSemPerfilSalvoTemCotaZeradaSemConsultarATabela() {
        when(perfilPrestadorService.buscarIdDoUsuario(USUARIO_ID)).thenReturn(Optional.empty());
        when(consultaPlanoPrestador.planoAtual(USUARIO_ID)).thenReturn(Plano.FREE);

        CotaDoMes cotaDoMes = useCase(Instant.parse("2026-10-29T15:00:00Z")).executar(USUARIO_ID,
                PapelUsuario.PRESTADOR);

        assertThat(cotaDoMes.cota().getEscolhasConsumidas()).isZero();
        verifyNoInteractions(cotaMensalService);
    }

    @Test
    void naViradaDoMesEmSaoPauloAContagemDoMesNovoComecaDoZero() {
        YearMonth novembro = YearMonth.of(2026, 11);
        when(perfilPrestadorService.buscarIdDoUsuario(USUARIO_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, novembro)).thenReturn(Optional.empty());
        when(consultaPlanoPrestador.planoAtual(USUARIO_ID)).thenReturn(Plano.FREE);

        CotaDoMes cotaDoMes = useCase(INICIO_NOVEMBRO).executar(USUARIO_ID, PapelUsuario.PRESTADOR);

        assertThat(cotaDoMes.cota().getAnoMes()).isEqualTo(novembro);
        assertThat(cotaDoMes.cota().getEscolhasConsumidas()).isZero();
        assertThat(cotaDoMes.isEsgotada()).isFalse();
        verify(consultaEscolhasDoPrestador).listarEscolhidasEntre(USUARIO_ID, INICIO_NOVEMBRO,
                Instant.parse("2026-12-01T03:00:00Z"));
    }

    @Test
    void goldNaoTemTetoENuncaEsgota() {
        when(perfilPrestadorService.buscarIdDoUsuario(USUARIO_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, OUTUBRO)).thenReturn(Optional.of(cota(OUTUBRO, 4, 4)));
        when(consultaPlanoPrestador.planoAtual(USUARIO_ID)).thenReturn(Plano.GOLD);

        CotaDoMes cotaDoMes = useCase(Instant.parse("2026-10-29T15:00:00Z")).executar(USUARIO_ID,
                PapelUsuario.PRESTADOR);

        assertThat(cotaDoMes.plano()).isEqualTo(Plano.GOLD);
        assertThat(cotaDoMes.teto()).isNull();
        assertThat(cotaDoMes.isEsgotada()).isFalse();
    }

    @Test
    void empregadorNaoTemCota() {
        assertThatThrownBy(() -> useCase(Instant.parse("2026-10-29T15:00:00Z")).executar(USUARIO_ID,
                PapelUsuario.EMPREGADOR))
                .isInstanceOf(SomentePrestadorConsultaCotaException.class);
        verifyNoInteractions(perfilPrestadorService, cotaMensalService, consultaPlanoPrestador,
                consultaEscolhasDoPrestador);
    }
}
