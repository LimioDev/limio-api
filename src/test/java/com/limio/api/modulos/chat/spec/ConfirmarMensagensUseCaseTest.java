package com.limio.api.modulos.chat.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.actions.usecase.ConfirmarMensagensUseCase;
import com.limio.api.modulos.chat.enums.Confirmacao;
import com.limio.api.modulos.chat.eventos.MensagensConfirmadasEvent;
import com.limio.api.modulos.chat.mensagem.Mensagem;
import com.limio.api.modulos.chat.mensagem.MensagemService;

@ExtendWith(MockitoExtension.class)
class ConfirmarMensagensUseCaseTest {

    private static final UUID CHAT_ID = UUID.randomUUID();
    private static final UUID CONTRATANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

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
        when(chatService.buscarDoParticipante(CHAT_ID, PRESTADOR_ID))
                .thenReturn(Chat.daCandidatura(UUID.randomUUID(), UUID.randomUUID(), CONTRATANTE_ID, PRESTADOR_ID));
    }

    private static Mensagem doEmpregador() {
        Mensagem mensagem = Mensagem.enviada(CHAT_ID, CONTRATANTE_ID, "Combinado", Instant.now());
        ReflectionTestUtils.setField(mensagem, "id", UUID.randomUUID());
        return mensagem;
    }

    @Test
    void dadasMensagensNaoEntregues_quandoChegamAoAparelho_entaoMarcaEntregueEAvisaORemetente() {
        Mensagem mensagem = doEmpregador();
        when(mensagemService.buscarNaoEntreguesPara(CHAT_ID, PRESTADOR_ID)).thenReturn(List.of(mensagem));

        useCase.executar(CHAT_ID, PRESTADOR_ID, Confirmacao.ENTREGUE);

        assertThat(mensagem.getEntregueEm()).isNotNull();
        assertThat(mensagem.getLidaEm()).isNull();
        verify(mensagemService).salvarTodas(List.of(mensagem));
        ArgumentCaptor<MensagensConfirmadasEvent> evento = ArgumentCaptor.forClass(MensagensConfirmadasEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().remetenteId()).isEqualTo(CONTRATANTE_ID);
        assertThat(evento.getValue().confirmacao()).isEqualTo(Confirmacao.ENTREGUE);
        assertThat(evento.getValue().mensagemIds()).containsExactly(mensagem.getId());
    }

    @Test
    void dadaMensagemAindaNaoEntregue_quandoAbreAConversa_entaoMarcaLidaEEntregueJuntas() {
        Mensagem mensagem = doEmpregador();
        when(mensagemService.buscarNaoLidasPor(CHAT_ID, PRESTADOR_ID)).thenReturn(List.of(mensagem));

        useCase.executar(CHAT_ID, PRESTADOR_ID, Confirmacao.LIDA);

        assertThat(mensagem.getLidaEm()).isNotNull();
        assertThat(mensagem.getEntregueEm()).isEqualTo(mensagem.getLidaEm());
    }

    @Test
    void dadoNadaPendente_quandoConfirma_entaoNaoGravaNemAvisa() {
        when(mensagemService.buscarNaoLidasPor(CHAT_ID, PRESTADOR_ID)).thenReturn(List.of());

        useCase.executar(CHAT_ID, PRESTADOR_ID, Confirmacao.LIDA);

        verify(mensagemService, never()).salvarTodas(any());
        verifyNoInteractions(eventos);
    }
}
