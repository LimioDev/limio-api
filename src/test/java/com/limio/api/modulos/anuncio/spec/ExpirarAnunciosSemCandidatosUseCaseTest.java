package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.ExpirarAnuncioUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.ExpirarAnunciosSemCandidatosUseCase;

@ExtendWith(MockitoExtension.class)
class ExpirarAnunciosSemCandidatosUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T12:00:00Z");
    private static final int LOTE = 50;

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private ExpirarAnuncioUseCase expirarAnuncio;

    private ExpirarAnunciosSemCandidatosUseCase job;

    @BeforeEach
    void montarJob() {
        job = new ExpirarAnunciosSemCandidatosUseCase(anuncioService, expirarAnuncio,
                Clock.fixed(AGORA, ZoneOffset.UTC), LOTE);
    }

    @Test
    void dadosCandidatos_quandoExecuta_entaoPedeAExpiracaoDeCadaUmComOInstanteDoRelogio() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(anuncioService.buscarIdsParaExpirar(AGORA, LOTE)).thenReturn(List.of(a, b));

        job.executar();

        verify(expirarAnuncio).executar(a, AGORA);
        verify(expirarAnuncio).executar(b, AGORA);
    }

    @Test
    void dadoNenhumCandidato_quandoExecuta_entaoNaoExpiraNada() {
        when(anuncioService.buscarIdsParaExpirar(AGORA, LOTE)).thenReturn(List.of());

        job.executar();

        verify(expirarAnuncio, never()).executar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dadaFalhaNumAnuncio_quandoExecuta_entaoLogaEContinuaComOsDemais() {
        UUID falha = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        when(anuncioService.buscarIdsParaExpirar(AGORA, LOTE)).thenReturn(List.of(falha, ok));
        when(expirarAnuncio.executar(falha, AGORA)).thenThrow(new IllegalStateException("falha simulada"));

        assertThatCode(job::executar).doesNotThrowAnyException();

        verify(expirarAnuncio).executar(ok, AGORA);
    }
}
