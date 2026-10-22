package com.limio.api.modulos.auth.spec;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.auth.actions.usecase.LimparRetencaoUseCase;
import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueadoService;
import com.limio.api.modulos.auth.sessao.SessaoService;

@ExtendWith(MockitoExtension.class)
class LimparRetencaoUseCaseTest {

    private static final Duration VALIDADE_LOG = Duration.ofDays(1825);
    private static final Duration VALIDADE_SESSAO = Duration.ofDays(180);

    @Mock
    private CpfBloqueadoService cpfBloqueadoService;

    @Mock
    private LogAuditoriaService logAuditoriaService;

    @Mock
    private SessaoService sessaoService;

    private LimparRetencaoUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new LimparRetencaoUseCase(cpfBloqueadoService, logAuditoriaService, sessaoService, VALIDADE_LOG,
                VALIDADE_SESSAO);
    }

    @Test
    void deveApagarCpfBloqueadoLiberadoLogAntigoESessaoAntigaComAsValidadesCertas() {
        Instant antes = Instant.now();

        useCase.executar();

        verify(cpfBloqueadoService).limparLiberados(any(Instant.class));

        ArgumentCaptor<Instant> limiteLog = ArgumentCaptor.forClass(Instant.class);
        verify(logAuditoriaService).limparAntigos(limiteLog.capture());
        assertAproximadamenteHaDuracao(antes, limiteLog.getValue(), VALIDADE_LOG);

        ArgumentCaptor<Instant> limiteSessao = ArgumentCaptor.forClass(Instant.class);
        verify(sessaoService).limparEncerradasOuExpiradas(limiteSessao.capture());
        assertAproximadamenteHaDuracao(antes, limiteSessao.getValue(), VALIDADE_SESSAO);
    }

    private static void assertAproximadamenteHaDuracao(Instant agoraAproximado, Instant limite, Duration validade) {
        Instant esperado = agoraAproximado.minus(validade);
        long diferencaSegundos = Math.abs(esperado.getEpochSecond() - limite.getEpochSecond());
        org.assertj.core.api.Assertions.assertThat(diferencaSegundos).isLessThan(5);
    }
}
