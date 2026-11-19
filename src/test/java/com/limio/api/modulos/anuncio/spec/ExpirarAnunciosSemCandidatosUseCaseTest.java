package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.comum.eventos.AnuncioExpiradoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.ExpirarAnunciosSemCandidatosUseCase;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

@ExtendWith(MockitoExtension.class)
class ExpirarAnunciosSemCandidatosUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-12T12:00:00Z");
    private static final int LOTE = 50;
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ApplicationEventPublisher eventos;

    @Mock
    private TravaTransacional trava;

    @Mock
    private PlatformTransactionManager gerenteTransacao;

    private ExpirarAnunciosSemCandidatosUseCase job;

    @BeforeEach
    void montarJob() {
        job = new ExpirarAnunciosSemCandidatosUseCase(anuncioService, aplicacaoService, eventos, trava,
                new TransactionTemplate(gerenteTransacao), Clock.fixed(AGORA, ZoneOffset.UTC), LOTE);
    }

    private Anuncio anuncio(StatusAnuncio status, Instant dataHoraPretendida) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .titulo("Instalar 3 tomadas")
                .dataHoraPretendida(dataHoraPretendida)
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private void candidato(Anuncio anuncio) {
        when(anuncioService.buscarIdsParaExpirar(AGORA, LOTE)).thenReturn(List.of(ANUNCIO_ID));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.ofNullable(anuncio));
    }

    @Test
    void dadoAnuncioVencidoSemCandidatura_quandoExecuta_entaoExpiraSalvaEPublicaEventoUmaVezEmTransacao() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, AGORA.minusSeconds(60));
        candidato(anuncio);
        when(aplicacaoService.existeAlguma(ANUNCIO_ID)).thenReturn(false);

        job.executar();

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getEncerradoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getMotivoEncerramento()).isEqualTo("Expirado sem candidatos");

        InOrder ordem = inOrder(gerenteTransacao, trava, anuncioService, eventos);
        ordem.verify(gerenteTransacao).getTransaction(any());
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(anuncioService).buscarPorId(ANUNCIO_ID);
        ordem.verify(anuncioService).salvar(anuncio);

        ArgumentCaptor<AnuncioExpiradoEvent> evento = ArgumentCaptor.forClass(AnuncioExpiradoEvent.class);
        ordem.verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isEqualTo(
                new AnuncioExpiradoEvent(ANUNCIO_ID, CONTRATANTE_ID, "Instalar 3 tomadas", AGORA));
        ordem.verify(gerenteTransacao).commit(any());
    }

    @Test
    void dadoAnuncioComCandidaturaEmQualquerEstado_quandoExecuta_entaoNaoExpiraNemPublica() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, AGORA.minusSeconds(60));
        candidato(anuncio);
        when(aplicacaoService.existeAlguma(ANUNCIO_ID)).thenReturn(true);

        job.executar();

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.PUBLICADO);
        assertThat(anuncio.getEncerradoEm()).isNull();
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoAnuncioAindaNoFuturo_quandoExecuta_entaoNaoExpira() {
        candidato(anuncio(StatusAnuncio.PUBLICADO, AGORA.plusSeconds(60)));

        job.executar();

        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoAnuncioJaEncerrado_quandoExecuta_entaoNaoReprocessaNemPublicaDeNovo() {
        Anuncio anuncio = anuncio(StatusAnuncio.ENCERRADO, AGORA.minusSeconds(60));
        candidato(anuncio);

        job.executar();

        assertThat(anuncio.getEncerradoEm()).isNull();
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoAnuncioInexistente_quandoExecuta_entaoNaoFazNada() {
        candidato(null);

        job.executar();

        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoNenhumCandidato_quandoExecuta_entaoNaoAbreTransacao() {
        when(anuncioService.buscarIdsParaExpirar(AGORA, LOTE)).thenReturn(List.of());

        job.executar();

        verifyNoInteractions(gerenteTransacao, trava, eventos);
    }

    @Test
    void dadaFalhaNumAnuncio_quandoExecuta_entaoFazRollbackLogaEContinuaComOsDemais() {
        UUID falha = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        Anuncio anuncioOk = anuncio(StatusAnuncio.PUBLICADO, AGORA.minusSeconds(60));
        ReflectionTestUtils.setField(anuncioOk, "id", ok);
        when(anuncioService.buscarIdsParaExpirar(AGORA, LOTE)).thenReturn(List.of(falha, ok));
        doThrow(new IllegalStateException("falha simulada")).when(trava).travar(EscopoTrava.ANUNCIO, falha.toString());
        when(anuncioService.buscarPorId(ok)).thenReturn(Optional.of(anuncioOk));

        assertThatCode(job::executar).doesNotThrowAnyException();

        verify(gerenteTransacao).rollback(any());
        verify(anuncioService).salvar(anuncioOk);
    }
}
