package com.limio.api.modulos.chat.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import com.limio.api.modulos.chat.actions.usecase.EnviarMensagemUseCase;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;
import com.limio.api.modulos.chat.eventos.MensagemEnviadaEvent;
import com.limio.api.modulos.chat.excecao.ChatNaoEncontradoException;
import com.limio.api.modulos.chat.excecao.ChatSomenteLeituraException;
import com.limio.api.modulos.chat.mensagem.Mensagem;
import com.limio.api.modulos.chat.mensagem.MensagemService;

@ExtendWith(MockitoExtension.class)
class EnviarMensagemUseCaseTest {

    private static final UUID CHAT_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @Mock
    private ChatService chatService;

    @Mock
    private MensagemService mensagemService;

    @Mock
    private ApplicationEventPublisher eventos;

    private EnviarMensagemUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new EnviarMensagemUseCase(chatService, mensagemService, eventos);
    }

    private Chat chat() {
        return Chat.builder()
                .aplicacaoId(UUID.randomUUID())
                .anuncioId(UUID.randomUUID())
                .contratanteId(CONTRATANTE_ID)
                .prestadorId(PRESTADOR_ID)
                .build();
    }

    @Test
    void dadoChatAberto_quandoEnvia_entaoGravaTextoExibidoIgualEAvisaAOutraParte() {
        when(chatService.buscarDoParticipante(CHAT_ID, PRESTADOR_ID)).thenReturn(chat());
        when(mensagemService.salvar(any())).thenAnswer(invocacao -> invocacao.getArgument(0));

        Mensagem mensagem = useCase.executar(CHAT_ID, PRESTADOR_ID, "Consigo quarta às 14h");

        assertThat(mensagem.getChatId()).isEqualTo(CHAT_ID);
        assertThat(mensagem.getRemetenteId()).isEqualTo(PRESTADOR_ID);
        assertThat(mensagem.getTexto()).isEqualTo("Consigo quarta às 14h");
        assertThat(mensagem.getTextoExibido()).isEqualTo(mensagem.getTexto());
        assertThat(mensagem.getDataEnvio()).isNotNull();
        assertThat(mensagem.getEntregueEm()).isNull();
        assertThat(mensagem.getLidaEm()).isNull();
        ArgumentCaptor<MensagemEnviadaEvent> evento = ArgumentCaptor.forClass(MensagemEnviadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().destinatarioId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().mensagem()).isSameAs(mensagem);
    }

    @Test
    void dadoChatSomenteLeitura_quandoEnvia_entaoRecusaSemGravar() {
        Chat chat = chat();
        chat.deixarSomenteLeitura(MotivoSomenteLeitura.CANDIDATURA_RECUSADA, Instant.now());
        when(chatService.buscarDoParticipante(CHAT_ID, PRESTADOR_ID)).thenReturn(chat);

        assertThatThrownBy(() -> useCase.executar(CHAT_ID, PRESTADOR_ID, "oi"))
                .isInstanceOf(ChatSomenteLeituraException.class);
        verify(mensagemService, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void dadoQuemNaoEParte_quandoEnvia_entaoChatNaoEncontrado() {
        UUID terceiro = UUID.randomUUID();
        when(chatService.buscarDoParticipante(CHAT_ID, terceiro)).thenThrow(new ChatNaoEncontradoException());

        assertThatThrownBy(() -> useCase.executar(CHAT_ID, terceiro, "oi"))
                .isInstanceOf(ChatNaoEncontradoException.class);
        verifyNoInteractions(mensagemService, eventos);
    }
}
