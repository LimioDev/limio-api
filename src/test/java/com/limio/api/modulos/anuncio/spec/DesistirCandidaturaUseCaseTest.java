package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.comum.eventos.CandidaturaEncerradaEvent;
import com.limio.api.modulos.anuncio.actions.usecase.DesistirCandidaturaUseCase;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.excecao.CandidaturaEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaNaoEncontradaException;
import com.limio.api.modulos.anuncio.excecao.UseCancelamentoServicoException;

@ExtendWith(MockitoExtension.class)
class DesistirCandidaturaUseCaseTest {

    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID CANDIDATURA_ID = UUID.randomUUID();
    private static final UUID ANUNCIO_ID = UUID.randomUUID();

    @Mock
    private AplicacaoService aplicacaoService;

    @Mock
    private ApplicationEventPublisher eventos;

    private DesistirCandidaturaUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new DesistirCandidaturaUseCase(aplicacaoService, eventos);
    }

    private Aplicacao aplicacao(UUID prestadorId, StatusAplicacao status) {
        Aplicacao aplicacao = Aplicacao.builder()
                .anuncioId(ANUNCIO_ID)
                .prestadorId(prestadorId)
                .status(status)
                .dataAplicacao(Instant.now())
                .build();
        ReflectionTestUtils.setField(aplicacao, "id", CANDIDATURA_ID);
        when(aplicacaoService.buscarParaAtualizar(CANDIDATURA_ID)).thenReturn(Optional.of(aplicacao));
        return aplicacao;
    }

    @Test
    void dadoCandidaturaEnviada_quandoDesiste_entaoEncerraComMotivoEPublicaEvento() {
        Aplicacao aplicacao = aplicacao(PRESTADOR_ID, StatusAplicacao.ENVIADA);

        useCase.executar(CANDIDATURA_ID, PRESTADOR_ID);

        assertThat(aplicacao.getStatus()).isEqualTo(StatusAplicacao.ENCERRADA);
        assertThat(aplicacao.getEncerradaEm()).isNotNull();
        assertThat(aplicacao.getMotivoEncerramento()).isEqualTo("Desistência do Prestador");
        verify(aplicacaoService).salvar(aplicacao);
        ArgumentCaptor<CandidaturaEncerradaEvent> evento = ArgumentCaptor.forClass(CandidaturaEncerradaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().candidaturaId()).isEqualTo(CANDIDATURA_ID);
        assertThat(evento.getValue().anuncioId()).isEqualTo(ANUNCIO_ID);
        assertThat(evento.getValue().prestadorId()).isEqualTo(PRESTADOR_ID);
        assertThat(evento.getValue().ocorridoEm()).isEqualTo(aplicacao.getEncerradaEm());
    }

    @Test
    void dadoCandidaturaInexistente_quandoDesiste_entaoNaoEncontrada() {
        when(aplicacaoService.buscarParaAtualizar(CANDIDATURA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CANDIDATURA_ID, PRESTADOR_ID))
                .isInstanceOf(CandidaturaNaoEncontradaException.class);
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoCandidaturaDeOutroPrestador_quandoDesiste_entaoNaoEncontradaSemAlterar() {
        Aplicacao alheia = aplicacao(UUID.randomUUID(), StatusAplicacao.ENVIADA);

        assertThatThrownBy(() -> useCase.executar(CANDIDATURA_ID, PRESTADOR_ID))
                .isInstanceOf(CandidaturaNaoEncontradaException.class);
        assertThat(alheia.getStatus()).isEqualTo(StatusAplicacao.ENVIADA);
        verify(aplicacaoService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAplicacao.class, names = { "ESCOLHIDA", "AGUARDANDO_VALIDACAO" })
    void dadoJaEscolhido_quandoDesiste_entaoIndicaCancelamentoDoServico(StatusAplicacao status) {
        aplicacao(PRESTADOR_ID, status);

        assertThatThrownBy(() -> useCase.executar(CANDIDATURA_ID, PRESTADOR_ID))
                .isInstanceOf(UseCancelamentoServicoException.class);
        verify(aplicacaoService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @ParameterizedTest
    @EnumSource(value = StatusAplicacao.class, names = { "RECUSADA", "CONCLUIDA", "ENCERRADA" })
    void dadoCandidaturaJaFinalizada_quandoDesiste_entaoEstadoInvalido(StatusAplicacao status) {
        aplicacao(PRESTADOR_ID, status);

        assertThatThrownBy(() -> useCase.executar(CANDIDATURA_ID, PRESTADOR_ID))
                .isInstanceOf(CandidaturaEstadoInvalidoException.class);
        verify(aplicacaoService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }
}
