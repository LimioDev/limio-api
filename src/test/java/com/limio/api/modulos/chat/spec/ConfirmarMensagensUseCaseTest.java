package com.limio.api.modulos.chat.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.actions.usecase.ConfirmarMensagensUseCase;
import com.limio.api.modulos.chat.enums.Confirmacao;
import com.limio.api.modulos.chat.eventos.MensagensConfirmadasEvent;
import com.limio.api.modulos.chat.mensagem.MensagemService;

@ExtendWith(MockitoExtension.class)
class ConfirmarMensagensUseCaseTest {

    private static final UUID CHAT_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID MENSAGEM_ID = UUID.randomUUID();

    @Mock
    private ChatService chatService;

    @Mock
    private MensagemService mensagemService;

    @Mock
    private ApplicationEventPublisher eventos;

    private ConfirmarMensagensUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConfirmarMensagensUseCase(chatService, mensagemService, eventos);
        when(chatService.buscarDoParticipante(CHAT_ID, PRESTADOR_ID)).thenReturn(Chat.builder()
                .aplicacaoId(UUID.randomUUID())
                .anuncioId(UUID.randomUUID())
                .contratanteId(CONTRATANTE_ID)
                .prestadorId(PRESTADOR_ID)
                .build());
    }

    @Test
    void dadasMensagensNaoEntregues_quandoChegamAoAparelho_entaoMarcaEntregueEAvisaORemetente() {
        when(mensagemService.marcarEntregues(eq(CHAT_ID), eq(PRESTADOR_ID), any())).thenReturn(List.of(MENSAGEM_ID));

        useCase.executar(CHAT_ID, PRESTADOR_ID, Confirmacao.ENTREGUE);

        verify(mensagemService, never()).marcarLidas(any(), any(), any());
        ArgumentCaptor<MensagensConfirmadasEvent> evento = ArgumentCaptor.forClass(MensagensConfirmadasEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().remetenteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().confirmacao()).isEqualTo(Confirmacao.ENTREGUE);
        assertThat(evento.getValue().mensagemIds()).containsExactly(MENSAGEM_ID);
    }

    @Test
    void dadasMensagensNaoLidas_quandoAbreAConversa_entaoMarcaLidaEAvisaORemetente() {
        when(mensagemService.marcarLidas(eq(CHAT_ID), eq(PRESTADOR_ID), any())).thenReturn(List.of(MENSAGEM_ID));

        useCase.executar(CHAT_ID, PRESTADOR_ID, Confirmacao.LIDA);

        verify(mensagemService, never()).marcarEntregues(any(), any(), any());
        ArgumentCaptor<MensagensConfirmadasEvent> evento = ArgumentCaptor.forClass(MensagensConfirmadasEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().confirmacao()).isEqualTo(Confirmacao.LIDA);
        assertThat(evento.getValue().mensagemIds()).containsExactly(MENSAGEM_ID);
    }

    @Test
    void dadoNadaPendente_quandoConfirma_entaoNaoAvisa() {
        when(mensagemService.marcarLidas(eq(CHAT_ID), eq(PRESTADOR_ID), any())).thenReturn(List.of());

        useCase.executar(CHAT_ID, PRESTADOR_ID, Confirmacao.LIDA);

        verifyNoInteractions(eventos);
    }
}
