package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.ServicoConcluidoEvent;
import com.limio.api.comum.eventos.enums.OrigemConclusao;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.ConcluirServicosPorSilencioUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

@ExtendWith(MockitoExtension.class)
class ConcluirServicosPorSilencioUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant LIMITE_AGENDA = AGORA.minus(Duration.ofHours(24));
    private static final int LOTE = 50;
    private static final UUID ANUNCIO_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ReagendamentoService reagendamentoService;

    @Mock
    private ConsultaDadosUsuario consultaDadosUsuario;

    @Mock
    private LogAuditoriaService logAuditoriaService;

    @Mock
    private ApplicationEventPublisher eventos;

    @Mock
    private TravaTransacional trava;

    @Mock
    private PlatformTransactionManager gerenteTransacao;

    private ConcluirServicosPorSilencioUseCase job;

    @BeforeEach
    void montarJob() {
        job = new ConcluirServicosPorSilencioUseCase(anuncioService, aplicacaoService, reagendamentoService,
                consultaDadosUsuario, logAuditoriaService, eventos, trava, new TransactionTemplate(gerenteTransacao),
                Clock.fixed(AGORA, ZoneOffset.UTC), 24, 24, LOTE);
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

    private Anuncio vencido() {
        return anuncio(StatusAnuncio.PUBLICADO, LIMITE_AGENDA.minusSeconds(60));
    }

    private void candidatos(UUID... ids) {
        when(anuncioService.buscarIdsParaConcluirPorSilencio(AGORA, LIMITE_AGENDA, 24, LOTE))
                .thenReturn(List.of(ids));
    }

    private void candidato(Anuncio anuncio) {
        candidatos(ANUNCIO_ID);
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
    }

    private void escolhido() {
        when(aplicacaoService.buscarEscolhidaVigente(ANUNCIO_ID)).thenReturn(Optional.of(
                Aplicacao.builder().prestadorId(PRESTADOR_ID).status(StatusAplicacao.ESCOLHIDA).build()));
    }

    private void contaAtiva(UUID usuarioId) {
        when(consultaDadosUsuario.buscarPorId(usuarioId))
                .thenReturn(Optional.of(new DadosUsuario("Nome", "a@b.com", null, AGORA)));
    }

    private void assertSemEfeito(Anuncio anuncio, StatusAnuncio statusEsperado) {
        assertThat(anuncio.getStatus()).isEqualTo(statusEsperado);
        assertThat(anuncio.getValidadoEm()).isNull();
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(logAuditoriaService, eventos);
    }

    @Test
    void dadoServicoVencido_quandoExecuta_entaoConcluiAuditaEPublicaUmaVezSobATravaEmTransacao() {
        Anuncio anuncio = vencido();
        candidato(anuncio);
        escolhido();
        contaAtiva(CONTRATANTE_ID);
        contaAtiva(PRESTADOR_ID);

        job.executar();

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.CONCLUIDO);
        assertThat(anuncio.getValidadoEm()).isEqualTo(AGORA);
        assertThat(anuncio.getEncerradoEm()).isEqualTo(AGORA);

        InOrder ordem = inOrder(gerenteTransacao, trava, anuncioService, reagendamentoService, logAuditoriaService,
                eventos);
        ordem.verify(gerenteTransacao).getTransaction(any());
        ordem.verify(trava).travar(EscopoTrava.ANUNCIO, ANUNCIO_ID.toString());
        ordem.verify(anuncioService).buscarPorId(ANUNCIO_ID);
        ordem.verify(reagendamentoService).existePendente(ANUNCIO_ID);
        ordem.verify(anuncioService).salvar(anuncio);
        ordem.verify(logAuditoriaService).registrar(any(), any(),
                eq(TipoAcaoAuditoria.SERVICO_CONCLUIDO_POR_SILENCIO), any(), any(), any());
        ArgumentCaptor<ServicoConcluidoEvent> evento = ArgumentCaptor.forClass(ServicoConcluidoEvent.class);
        ordem.verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new ServicoConcluidoEvent(ANUNCIO_ID, "Instalar 3 tomadas",
                CONTRATANTE_ID, PRESTADOR_ID, OrigemConclusao.SILENCIO, AGORA));
        ordem.verify(gerenteTransacao).commit(any());
    }

    @Test
    void dadoEstadoQueMudouDepoisDaPreSelecao_quandoExecuta_entaoNaoConclui() {
        Anuncio anuncio = anuncio(StatusAnuncio.ENCERRADO, LIMITE_AGENDA.minusSeconds(60));
        candidato(anuncio);

        job.executar();

        assertSemEfeito(anuncio, StatusAnuncio.ENCERRADO);
    }

    @Test
    void dadaDataQueMudouDepoisDaPreSelecao_quandoExecuta_entaoNaoConclui() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, AGORA.plus(Duration.ofDays(1)));
        candidato(anuncio);

        job.executar();

        assertSemEfeito(anuncio, StatusAnuncio.PUBLICADO);
    }

    @Test
    void dadoSemCandidaturaEscolhidaVigente_quandoExecuta_entaoNaoConclui() {
        Anuncio anuncio = vencido();
        candidato(anuncio);
        when(aplicacaoService.buscarEscolhidaVigente(ANUNCIO_ID)).thenReturn(Optional.empty());

        job.executar();

        assertSemEfeito(anuncio, StatusAnuncio.PUBLICADO);
    }

    @Test
    void dadaPropostaDeReagendamentoPendente_quandoExecuta_entaoNaoConclui() {
        Anuncio anuncio = vencido();
        candidato(anuncio);
        escolhido();
        when(reagendamentoService.existePendente(ANUNCIO_ID)).thenReturn(true);

        job.executar();

        assertSemEfeito(anuncio, StatusAnuncio.PUBLICADO);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void dadaContaEncerradaDeUmaDasPartes_quandoExecuta_entaoNaoConclui(boolean doContratante) {
        Anuncio anuncio = vencido();
        candidato(anuncio);
        escolhido();
        if (doContratante) {
            when(consultaDadosUsuario.buscarPorId(CONTRATANTE_ID)).thenReturn(Optional.empty());
        } else {
            contaAtiva(CONTRATANTE_ID);
            when(consultaDadosUsuario.buscarPorId(PRESTADOR_ID)).thenReturn(Optional.empty());
        }

        job.executar();

        assertSemEfeito(anuncio, StatusAnuncio.PUBLICADO);
    }

    @Test
    void dadaFalhaNumAnuncio_quandoExecuta_entaoOsDemaisContinuam() {
        UUID comFalha = UUID.randomUUID();
        Anuncio anuncio = vencido();
        candidatos(comFalha, ANUNCIO_ID);
        when(anuncioService.buscarPorId(comFalha)).thenThrow(new IllegalStateException("falha simulada"));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        escolhido();
        contaAtiva(CONTRATANTE_ID);
        contaAtiva(PRESTADOR_ID);

        job.executar();

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.CONCLUIDO);
        verify(gerenteTransacao).rollback(any());
        verify(gerenteTransacao).commit(any());
    }
}
