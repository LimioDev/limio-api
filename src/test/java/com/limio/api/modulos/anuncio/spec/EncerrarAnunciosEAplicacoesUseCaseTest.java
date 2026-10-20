package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.AvisoDeAnuncioEvent;
import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.actions.usecase.EncerrarAnunciosEAplicacoesUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

@ExtendWith(MockitoExtension.class)
class EncerrarAnunciosEAplicacoesUseCaseTest {

    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();

    @Mock
    private AnuncioService anuncioService;

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ReagendamentoService reagendamentoService;

    @Mock
    private LogAuditoriaService logAuditoriaService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EncerrarAnunciosEAplicacoesUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EncerrarAnunciosEAplicacoesUseCase(anuncioService, aplicacaoService, reagendamentoService,
                logAuditoriaService, eventPublisher);
        Mockito.lenient().when(reagendamentoService.buscarPendentesDosAnuncios(any())).thenReturn(List.of());
    }

    private Anuncio anuncio(StatusAnuncio status, Instant dataHoraPretendida) {
        Anuncio anuncio = Anuncio.builder()
                .contratanteId(CONTRATANTE_ID)
                .descricao("Faxina")
                .dataHoraPretendida(dataHoraPretendida)
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private Aplicacao aplicacao(UUID anuncioId, StatusAplicacao status) {
        return Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(PRESTADOR_ID)
                .status(status)
                .dataAplicacao(Instant.now())
                .build();
    }

    @Test
    void dadoAnuncioAbertoSemCandidaturaEscolhida_quandoContaDoContratanteEncerra_entaoSoEncerraOAnuncio() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, Instant.now().plus(Duration.ofDays(10)));
        when(anuncioService.buscarAbertosDoContratante(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDoPrestador(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.empty());

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getEncerradoEm()).isNotNull();
        verify(anuncioService).salvar(anuncio);
        verifyNoInteractions(eventPublisher, logAuditoriaService);
    }

    @Test
    void dadoAnuncioComCandidaturaEscolhidaForaDaJanelaDe24h_quandoContratanteEncerraConta_entaoAvisaSemAuditoria() {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO, Instant.now().plus(Duration.ofDays(5)));
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, StatusAplicacao.ESCOLHIDA);
        when(anuncioService.buscarAbertosDoContratante(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDoPrestador(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        ArgumentCaptor<AvisoDeAnuncioEvent> aviso = ArgumentCaptor.forClass(AvisoDeAnuncioEvent.class);
        verify(eventPublisher).publishEvent(aviso.capture());
        assertThat(aviso.getValue().destinatarioId()).isEqualTo(PRESTADOR_ID);
        assertThat(aviso.getValue().assunto()).isEqualTo("Anúncio encerrado");
        verifyNoInteractions(logAuditoriaService);
    }

    @Test
    void dadoAnuncioComCandidaturaEscolhidaDentroDaJanelaDe24h_quandoContratanteEncerraConta_entaoRegistraAuditoriaEAvisaComoImpossibilidadeOperacional() {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO, Instant.now().plus(Duration.ofHours(10)));
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, StatusAplicacao.ESCOLHIDA);
        when(anuncioService.buscarAbertosDoContratante(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDoPrestador(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.of(escolhida));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        verify(logAuditoriaService).registrar(eq("Anuncio"), eq(ANUNCIO_ID), eq(TipoAcaoAuditoria.COMBINADO_CANCELADO),
                any(), isNull());
        ArgumentCaptor<AvisoDeAnuncioEvent> aviso = ArgumentCaptor.forClass(AvisoDeAnuncioEvent.class);
        verify(eventPublisher).publishEvent(aviso.capture());
        assertThat(aviso.getValue().assunto()).isEqualTo("Combinado cancelado por impossibilidade operacional");
    }

    @Test
    void dadoReagendamentoPendenteDoAnuncio_quandoContratanteEncerraConta_entaoCancelaSemEfeito() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, Instant.now().plus(Duration.ofDays(10)));
        Reagendamento pendente = Reagendamento.builder()
                .anuncioId(ANUNCIO_ID)
                .dataAnterior(Instant.now())
                .dataProposta(Instant.now().plus(Duration.ofDays(2)))
                .status(StatusReagendamento.PENDENTE)
                .build();
        when(anuncioService.buscarAbertosDoContratante(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDoPrestador(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarEscolhidaDoAnuncio(ANUNCIO_ID)).thenReturn(Optional.empty());
        when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of(pendente));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        assertThat(pendente.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        verify(reagendamentoService).salvar(pendente);
    }

    @Test
    void dadoCandidaturaEnviadaNaoEscolhida_quandoPrestadorEncerraConta_entaoSoEncerraACandidatura() {
        Aplicacao enviada = aplicacao(ANUNCIO_ID, StatusAplicacao.ENVIADA);
        when(anuncioService.buscarAbertosDoContratante(PRESTADOR_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDoPrestador(PRESTADOR_ID)).thenReturn(List.of(enviada));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(PRESTADOR_ID, Instant.now()));

        assertThat(enviada.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(enviada.getMotivoEncerramento()).isEqualTo("Cancelado por saída de conta");
        verify(aplicacaoService).salvar(enviada);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void dadoCandidaturaEscolhida_quandoPrestadorEncerraConta_entaoEncerraCandidaturaEOAnuncioEAvisaContratante() {
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, StatusAplicacao.ESCOLHIDA);
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO, Instant.now().plus(Duration.ofDays(5)));
        when(anuncioService.buscarAbertosDoContratante(PRESTADOR_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDoPrestador(PRESTADOR_ID)).thenReturn(List.of(escolhida));
        when(anuncioService.buscarPorIdOuFalhar(ANUNCIO_ID)).thenReturn(anuncio);

        useCase.aoEncerrarConta(new ContaEncerradaEvent(PRESTADOR_ID, Instant.now()));

        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        verify(anuncioService).salvar(anuncio);

        ArgumentCaptor<AvisoDeAnuncioEvent> aviso = ArgumentCaptor.forClass(AvisoDeAnuncioEvent.class);
        verify(eventPublisher).publishEvent(aviso.capture());
        assertThat(aviso.getValue().destinatarioId()).isEqualTo(CONTRATANTE_ID);
        assertThat(aviso.getValue().assunto()).isEqualTo("Candidatura encerrada");
    }

    @Test
    void dadoCandidaturaEscolhidaMasAnuncioJaConcluido_quandoPrestadorEncerraConta_entaoNaoMexeNoAnuncioDeNovo() {
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, StatusAplicacao.ESCOLHIDA);
        Anuncio concluido = anuncio(StatusAnuncio.CONCLUIDO, Instant.now().minus(Duration.ofDays(1)));
        when(anuncioService.buscarAbertosDoContratante(PRESTADOR_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDoPrestador(PRESTADOR_ID)).thenReturn(List.of(escolhida));
        when(anuncioService.buscarPorIdOuFalhar(ANUNCIO_ID)).thenReturn(concluido);

        useCase.aoEncerrarConta(new ContaEncerradaEvent(PRESTADOR_ID, Instant.now()));

        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventPublisher);
    }
}
