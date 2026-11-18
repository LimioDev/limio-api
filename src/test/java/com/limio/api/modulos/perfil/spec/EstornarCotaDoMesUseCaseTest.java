package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.perfil.actions.usecase.EstornarCotaDoMesUseCase;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensal;
import com.limio.api.modulos.perfil.perfilprestador.cotamensal.CotaMensalService;

@ExtendWith(MockitoExtension.class)
class EstornarCotaDoMesUseCaseTest {

    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID PERFIL_ID = UUID.randomUUID();
    private static final int TETO = 4;
    /** 29/10/2026, meio-dia em São Paulo: quando a escolha foi consumida. */
    private static final Instant ESCOLHIDA_EM = Instant.parse("2026-10-29T15:00:00Z");
    private static final YearMonth MES = YearMonth.of(2026, 10);

    @Mock
    private PerfilPrestadorService perfilPrestadorService;

    @Mock
    private CotaMensalService cotaMensalService;

    @Mock
    private TravaTransacional trava;

    private EstornarCotaDoMesUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EstornarCotaDoMesUseCase(perfilPrestadorService, cotaMensalService, trava);
    }

    private CotaMensal cota(YearMonth mes, int consumidas) {
        CotaMensal cota = CotaMensal.zerada(PERFIL_ID, mes, TETO);
        ReflectionTestUtils.setField(cota, "escolhasConsumidas", consumidas);
        return cota;
    }

    @Test
    void dadaCotaComEscolhaConsumida_quandoEstorna_entaoDecrementaSalvaETravaAntesDeLer() {
        CotaMensal existente = cota(MES, 3);
        when(perfilPrestadorService.buscarIdDoUsuario(PRESTADOR_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, MES)).thenReturn(Optional.of(existente));

        assertThat(useCase.executar(PRESTADOR_ID, ESCOLHIDA_EM)).isTrue();

        assertThat(existente.getEscolhasConsumidas()).isEqualTo(2);
        InOrder ordem = inOrder(trava, perfilPrestadorService, cotaMensalService);
        ordem.verify(trava).travar(EscopoTrava.COTA_PRESTADOR, PRESTADOR_ID.toString());
        ordem.verify(perfilPrestadorService).buscarIdDoUsuario(PRESTADOR_ID);
        ordem.verify(cotaMensalService).buscarDoMes(PERFIL_ID, MES);
        ordem.verify(cotaMensalService).salvar(existente);
    }

    @Test
    void dadoCancelamentoEmOutroMes_quandoEstorna_entaoDevolveNaCotaDoMesDaEscolha() {
        // escolhida em 31/10 às 23:59 de São Paulo (já é 01/11 em UTC); o estorno cai no mês da escolha
        Instant escolhidaNoFimDeOutubro = Instant.parse("2026-11-01T02:59:00Z");
        CotaMensal outubro = cota(MES, 4);
        when(perfilPrestadorService.buscarIdDoUsuario(PRESTADOR_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, MES)).thenReturn(Optional.of(outubro));

        assertThat(useCase.executar(PRESTADOR_ID, escolhidaNoFimDeOutubro)).isTrue();

        assertThat(outubro.getEscolhasConsumidas()).isEqualTo(3);
        verify(cotaMensalService, never()).buscarDoMes(PERFIL_ID, YearMonth.of(2026, 11));
    }

    @Test
    void dadoMesSemLinha_quandoEstorna_entaoDevolveFalsoSemCriarLinha() {
        when(perfilPrestadorService.buscarIdDoUsuario(PRESTADOR_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, MES)).thenReturn(Optional.empty());

        assertThat(useCase.executar(PRESTADOR_ID, ESCOLHIDA_EM)).isFalse();

        verify(cotaMensalService, never()).salvar(any());
    }

    @Test
    void dadaCotaJaZerada_quandoEstorna_entaoDevolveFalsoSemSalvar() {
        CotaMensal zerada = cota(MES, 0);
        when(perfilPrestadorService.buscarIdDoUsuario(PRESTADOR_ID)).thenReturn(Optional.of(PERFIL_ID));
        when(cotaMensalService.buscarDoMes(PERFIL_ID, MES)).thenReturn(Optional.of(zerada));

        assertThat(useCase.executar(PRESTADOR_ID, ESCOLHIDA_EM)).isFalse();

        assertThat(zerada.getEscolhasConsumidas()).isZero();
        verify(cotaMensalService, never()).salvar(any());
    }

    @Test
    void dadoPrestadorSemPerfil_quandoEstorna_entaoDevolveFalsoSemSalvar() {
        when(perfilPrestadorService.buscarIdDoUsuario(PRESTADOR_ID)).thenReturn(Optional.empty());

        assertThat(useCase.executar(PRESTADOR_ID, ESCOLHIDA_EM)).isFalse();

        verify(cotaMensalService, never()).salvar(any());
    }
}
