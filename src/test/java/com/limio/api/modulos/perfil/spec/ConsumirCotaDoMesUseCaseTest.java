package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.perfil.actions.usecase.ConsumirCotaDoMesUseCase;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensalService;

@ExtendWith(MockitoExtension.class)
class ConsumirCotaDoMesUseCaseTest {

    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID PERFIL_ID = UUID.randomUUID();
    private static final int TETO = 4;
    /** 29/10/2026, meio-dia em São Paulo. */
    private static final Instant OUTUBRO = Instant.parse("2026-10-29T15:00:00Z");
    private static final YearMonth MES = YearMonth.of(2026, 10);

    @Mock
    private PerfilPrestadorService perfilPrestadorService;

    @Mock
    private CotaMensalService cotaMensalService;

    @Mock
    private TravaTransacional trava;

    private ConsumirCotaDoMesUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsumirCotaDoMesUseCase(perfilPrestadorService, cotaMensalService, trava, TETO);
    }

    private CotaMensal cota(int consumidas) {
        CotaMensal cota = CotaMensal.zerada(PERFIL_ID, MES, TETO);
        ReflectionTestUtils.setField(cota, "escolhasConsumidas", consumidas);
        return cota;
    }

    @Test
    void dadoMesSemLinha_quandoConsome_entaoCriaALinhaComUmaEscolhaETetoFree() {
        when(perfilPrestadorService.buscarIdDoUsuario(PRESTADOR_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, MES)).thenReturn(Optional.empty());

        boolean consumiu = useCase.executar(PRESTADOR_ID, OUTUBRO);

        assertThat(consumiu).isTrue();
        ArgumentCaptor<CotaMensal> salva = ArgumentCaptor.forClass(CotaMensal.class);
        verify(cotaMensalService).salvar(salva.capture());
        assertThat(salva.getValue().getPerfilPrestadorId()).isEqualTo(PERFIL_ID);
        assertThat(salva.getValue().getAnoMes()).isEqualTo(MES);
        assertThat(salva.getValue().getEscolhasConsumidas()).isEqualTo(1);
        assertThat(salva.getValue().getTetoAplicado()).isEqualTo(TETO);
    }

    @Test
    void dadoCotaComSaldo_quandoConsome_entaoIncrementaSalvaETravaAntesDeLer() {
        CotaMensal existente = cota(3);
        when(perfilPrestadorService.buscarIdDoUsuario(PRESTADOR_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, MES)).thenReturn(Optional.of(existente));

        assertThat(useCase.executar(PRESTADOR_ID, OUTUBRO)).isTrue();

        assertThat(existente.getEscolhasConsumidas()).isEqualTo(4);
        verify(cotaMensalService).salvar(existente);
        InOrder ordem = inOrder(trava, perfilPrestadorService, cotaMensalService);
        ordem.verify(trava).travar(EscopoTrava.COTA_PRESTADOR, PRESTADOR_ID.toString());
        ordem.verify(perfilPrestadorService).buscarIdDoUsuario(PRESTADOR_ID);
        ordem.verify(cotaMensalService).buscarDoMes(PERFIL_ID, MES);
        ordem.verify(cotaMensalService).salvar(existente);
    }

    @Test
    void dadoCotaEsgotada_quandoConsome_entaoDevolveFalsoSemAlterarNada() {
        CotaMensal esgotada = cota(TETO);
        when(perfilPrestadorService.buscarIdDoUsuario(PRESTADOR_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, MES)).thenReturn(Optional.of(esgotada));

        assertThat(useCase.executar(PRESTADOR_ID, OUTUBRO)).isFalse();

        assertThat(esgotada.getEscolhasConsumidas()).isEqualTo(TETO);
        verify(cotaMensalService, never()).salvar(any());
    }

    @Test
    void dadoOMesNovo_quandoConsome_entaoNaoHerdaOConsumoDoMesAnterior() {
        Instant novembro = Instant.parse("2026-11-01T03:00:00Z");
        when(perfilPrestadorService.buscarIdDoUsuario(PRESTADOR_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, YearMonth.of(2026, 11))).thenReturn(Optional.empty());

        assertThat(useCase.executar(PRESTADOR_ID, novembro)).isTrue();

        ArgumentCaptor<CotaMensal> salva = ArgumentCaptor.forClass(CotaMensal.class);
        verify(cotaMensalService).salvar(salva.capture());
        assertThat(salva.getValue().getAnoMes()).isEqualTo(YearMonth.of(2026, 11));
        assertThat(salva.getValue().getEscolhasConsumidas()).isEqualTo(1);
    }

    @Test
    void dadoPrestadorSemPerfil_quandoConsome_entaoFalhaSemSalvar() {
        when(perfilPrestadorService.buscarIdDoUsuario(PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PRESTADOR_ID, OUTUBRO)).isInstanceOf(IllegalStateException.class);

        verify(cotaMensalService, never()).salvar(any());
    }
}
