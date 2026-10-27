package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    private static final UUID OUTRO_PRESTADOR_ID = UUID.randomUUID();
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
                .titulo("Faxina")
                .contratanteId(CONTRATANTE_ID)
                .descricao("Faxina")
                .dataHoraPretendida(dataHoraPretendida)
                .status(status)
                .build();
        ReflectionTestUtils.setField(anuncio, "id", ANUNCIO_ID);
        return anuncio;
    }

    private Aplicacao aplicacao(UUID anuncioId, UUID prestadorId, StatusAplicacao status) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(anuncioId)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(Instant.now())
                .build();
        ReflectionTestUtils.setField(aplicacao, "id", UUID.randomUUID());
        return aplicacao;
    }

    @Test
    void dadoAnuncioAbertoSemCandidaturaEscolhida_quandoContaDoContratanteEncerra_entaoSoEncerraOAnuncio() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, Instant.now().plus(Duration.ofDays(10)));
        when(anuncioService.buscarAbertosDoContratante(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDoPrestador(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of());

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        assertThat(anuncio.getEncerradoEm()).isNotNull();
        verify(anuncioService).salvar(anuncio);
        verifyNoInteractions(eventPublisher, logAuditoriaService);
    }

    @Test
    void dadoAnuncioComCandidaturaEscolhidaForaDaJanelaDe24h_quandoContratanteEncerraConta_entaoAvisaSemAuditoria() {
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO, Instant.now().plus(Duration.ofDays(5)));
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.ESCOLHIDA);
        when(anuncioService.buscarAbertosDoContratante(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDoPrestador(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of(escolhida));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        ArgumentCaptor<AvisoDeAnuncioEvent> aviso = ArgumentCaptor.forClass(AvisoDeAnuncioEvent.class);
        verify(eventPublisher).publishEvent(aviso.capture());
        assertThat(aviso.getValue().destinatarioId()).isEqualTo(PRESTADOR_ID);
        assertThat(aviso.getValue().assunto()).isEqualTo("Anúncio encerrado");
        verifyNoInteractions(logAuditoriaService);
    }

    @Test
    void dadoAnuncioAindaPublicadoComCandidaturaEscolhidaDentroDaJanelaDe24h_quandoContratanteEncerraConta_entaoRegistraAuditoriaEAvisaComoImpossibilidadeOperacional() {
        // Status PUBLICADO (não EM_ANDAMENTO) de propósito: o check-in só move pra EM_ANDAMENTO
        // depois de dataHoraPretendida, então "combinado <24h" tem que disparar sem depender
        // desse status (achado de review corrigido em Anuncio.estaAMenosDe).
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, Instant.now().plus(Duration.ofHours(10)));
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.ESCOLHIDA);
        when(anuncioService.buscarAbertosDoContratante(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDoPrestador(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of(escolhida));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        verify(logAuditoriaService).registrar(eq("Anuncio"), eq(ANUNCIO_ID), eq(TipoAcaoAuditoria.COMBINADO_CANCELADO),
                any(), isNull());
        ArgumentCaptor<AvisoDeAnuncioEvent> aviso = ArgumentCaptor.forClass(AvisoDeAnuncioEvent.class);
        verify(eventPublisher).publishEvent(aviso.capture());
        assertThat(aviso.getValue().assunto()).isEqualTo("Combinado cancelado por impossibilidade operacional");
    }

    @Test
    void dadoAnuncioComCandidaturaEscolhidaEEnviada_quandoContratanteEncerraConta_entaoEncerraEAvisaAsDuas() {
        Anuncio anuncio = anuncio(StatusAnuncio.PUBLICADO, Instant.now().plus(Duration.ofDays(10)));
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.ESCOLHIDA);
        Aplicacao enviada = aplicacao(ANUNCIO_ID, OUTRO_PRESTADOR_ID, StatusAplicacao.ENVIADA);
        when(anuncioService.buscarAbertosDoContratante(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDoPrestador(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of(escolhida, enviada));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(enviada.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        verify(aplicacaoService).salvar(escolhida);
        verify(aplicacaoService).salvar(enviada);

        ArgumentCaptor<AvisoDeAnuncioEvent> avisos = ArgumentCaptor.forClass(AvisoDeAnuncioEvent.class);
        verify(eventPublisher, Mockito.times(2)).publishEvent(avisos.capture());
        assertThat(avisos.getAllValues())
                .extracting(AvisoDeAnuncioEvent::destinatarioId)
                .containsExactlyInAnyOrder(PRESTADOR_ID, OUTRO_PRESTADOR_ID);
    }

    @Test
    void dadoAnuncioComCandidaturaAguardandoValidacao_quandoContratanteEncerraConta_entaoEncerraEAvisaIgualEscolhida() {
        Anuncio anuncio = anuncio(StatusAnuncio.AGUARDANDO_VALIDACAO, Instant.now().minus(Duration.ofHours(2)));
        Aplicacao aguardando = aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.AGUARDANDO_VALIDACAO);
        when(anuncioService.buscarAbertosDoContratante(CONTRATANTE_ID)).thenReturn(List.of(anuncio));
        when(aplicacaoService.buscarAbertasDoPrestador(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of(aguardando));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        assertThat(aguardando.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(aguardando.getMotivoEncerramento()).isEqualTo("Cancelado por saída de conta");
        verify(eventPublisher).publishEvent(any(AvisoDeAnuncioEvent.class));
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
        when(aplicacaoService.buscarAbertasDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of());
        when(reagendamentoService.buscarPendentesDosAnuncios(List.of(ANUNCIO_ID))).thenReturn(List.of(pendente));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        assertThat(pendente.getStatus()).isEqualTo(StatusReagendamento.CANCELADO);
        verify(reagendamentoService).salvar(pendente);
    }

    @Test
    void dadoCandidaturaEnviadaNaoEscolhida_quandoPrestadorEncerraConta_entaoSoEncerraACandidatura() {
        Aplicacao enviada = aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.ENVIADA);
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
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.ESCOLHIDA);
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO, Instant.now().plus(Duration.ofDays(5)));
        when(anuncioService.buscarAbertosDoContratante(PRESTADOR_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDoPrestador(PRESTADOR_ID)).thenReturn(List.of(escolhida));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarAbertasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of());

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
    void dadoCandidaturaAguardandoValidacao_quandoPrestadorEncerraConta_entaoEncerraOAnuncioIgualEscolhida() {
        Aplicacao aguardando = aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.AGUARDANDO_VALIDACAO);
        Anuncio anuncio = anuncio(StatusAnuncio.AGUARDANDO_VALIDACAO, Instant.now().minus(Duration.ofHours(1)));
        when(anuncioService.buscarAbertosDoContratante(PRESTADOR_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDoPrestador(PRESTADOR_ID)).thenReturn(List.of(aguardando));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarAbertasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of());

        useCase.aoEncerrarConta(new ContaEncerradaEvent(PRESTADOR_ID, Instant.now()));

        assertThat(anuncio.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        verify(anuncioService).salvar(anuncio);
    }

    @Test
    void dadoPrestadorEscolhidoComOutraCandidaturaEnviadaNoMesmoAnuncio_quandoPrestadorEncerraConta_entaoFechaEAvisaAOutraTambem() {
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.ESCOLHIDA);
        Aplicacao enviadaDeOutroPrestador = aplicacao(ANUNCIO_ID, OUTRO_PRESTADOR_ID, StatusAplicacao.ENVIADA);
        Anuncio anuncio = anuncio(StatusAnuncio.EM_ANDAMENTO, Instant.now().plus(Duration.ofDays(5)));
        when(anuncioService.buscarAbertosDoContratante(PRESTADOR_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDoPrestador(PRESTADOR_ID)).thenReturn(List.of(escolhida));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(anuncio));
        when(aplicacaoService.buscarAbertasDoAnuncio(ANUNCIO_ID)).thenReturn(List.of(enviadaDeOutroPrestador));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(PRESTADOR_ID, Instant.now()));

        assertThat(enviadaDeOutroPrestador.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        verify(aplicacaoService).salvar(enviadaDeOutroPrestador);

        ArgumentCaptor<AvisoDeAnuncioEvent> avisos = ArgumentCaptor.forClass(AvisoDeAnuncioEvent.class);
        verify(eventPublisher, Mockito.times(2)).publishEvent(avisos.capture());
        assertThat(avisos.getAllValues())
                .extracting(AvisoDeAnuncioEvent::destinatarioId)
                .containsExactlyInAnyOrder(CONTRATANTE_ID, OUTRO_PRESTADOR_ID);
    }

    @Test
    void dadoCandidaturaEscolhidaMasAnuncioJaConcluido_quandoPrestadorEncerraConta_entaoNaoMexeNoAnuncioDeNovo() {
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.ESCOLHIDA);
        Anuncio concluido = anuncio(StatusAnuncio.CONCLUIDO, Instant.now().minus(Duration.ofDays(1)));
        when(anuncioService.buscarAbertosDoContratante(PRESTADOR_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDoPrestador(PRESTADOR_ID)).thenReturn(List.of(escolhida));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.of(concluido));

        useCase.aoEncerrarConta(new ContaEncerradaEvent(PRESTADOR_ID, Instant.now()));

        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void dadoAnuncioLigadoInexistente_quandoPrestadorEncerraConta_entaoCandidaturaFechaMasAnuncioEPulado() {
        Aplicacao escolhida = aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.ESCOLHIDA);
        when(anuncioService.buscarAbertosDoContratante(PRESTADOR_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDoPrestador(PRESTADOR_ID)).thenReturn(List.of(escolhida));
        when(anuncioService.buscarPorId(ANUNCIO_ID)).thenReturn(Optional.empty());

        useCase.aoEncerrarConta(new ContaEncerradaEvent(PRESTADOR_ID, Instant.now()));

        assertThat(escolhida.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        verify(aplicacaoService).salvar(escolhida);
        verify(anuncioService, never()).salvar(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void dadoUmItemFalhandoNoProcessamento_quandoContaEncerra_entaoOsDemaisItensContinuamProcessados() {
        UUID outroAnuncioId = UUID.randomUUID();
        Anuncio anuncioQuebrado = anuncio(StatusAnuncio.PUBLICADO, Instant.now().plus(Duration.ofDays(10)));
        Anuncio anuncioOk = Anuncio.builder()
                .titulo("Jardinagem")
                .contratanteId(CONTRATANTE_ID)
                .descricao("Jardinagem")
                .dataHoraPretendida(Instant.now().plus(Duration.ofDays(10)))
                .status(StatusAnuncio.PUBLICADO)
                .build();
        ReflectionTestUtils.setField(anuncioOk, "id", outroAnuncioId);

        when(anuncioService.buscarAbertosDoContratante(CONTRATANTE_ID)).thenReturn(List.of(anuncioQuebrado, anuncioOk));
        when(aplicacaoService.buscarAbertasDoPrestador(CONTRATANTE_ID)).thenReturn(List.of());
        when(aplicacaoService.buscarAbertasDosAnuncios(anyList()))
                .thenReturn(List.of(aplicacao(ANUNCIO_ID, PRESTADOR_ID, StatusAplicacao.ESCOLHIDA)));
        Mockito.doThrow(new RuntimeException("falha simulada")).when(anuncioService).salvar(anuncioQuebrado);

        useCase.aoEncerrarConta(new ContaEncerradaEvent(CONTRATANTE_ID, Instant.now()));

        assertThat(anuncioOk.getStatus()).isEqualTo(StatusAnuncio.ENCERRADO);
        verify(anuncioService).salvar(anuncioOk);
    }
}
