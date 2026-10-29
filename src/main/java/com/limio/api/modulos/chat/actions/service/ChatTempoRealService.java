package com.limio.api.modulos.chat.actions.service;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.modulos.chat.actions.mapper.ChatMapper;
import com.limio.api.modulos.chat.eventos.MensagemEnviadaEvent;
import com.limio.api.modulos.chat.eventos.MensagensConfirmadasEvent;
import com.limio.api.modulos.chat.records.EventoChatResponse;

/**
 * Entrega em tempo real pelo WebSocket ({@code /user/queue/chat}), sempre depois do commit: o aparelho nunca recebe
 * algo que acabou desfeito. Quem não está conectado não recebe nada aqui e vê tudo ao abrir o chat (o histórico é a
 * fonte da verdade). Sem {@code fallbackExecution}: os usecases que publicam são {@code @Transactional}.
 */
@Component
public class ChatTempoRealService {

    public static final String FILA_CHAT = "/queue/chat";

    private final SimpMessagingTemplate mensageiro;
    private final ChatMapper chatMapper;

    public ChatTempoRealService(SimpMessagingTemplate mensageiro, ChatMapper chatMapper) {
        this.mensageiro = mensageiro;
        this.chatMapper = chatMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoEnviarMensagem(MensagemEnviadaEvent evento) {
        entregar(evento.destinatarioId(), chatMapper.toEvento(evento));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConfirmarMensagens(MensagensConfirmadasEvent evento) {
        entregar(evento.remetenteId(), chatMapper.toEvento(evento));
    }

    private void entregar(UUID usuarioId, EventoChatResponse evento) {
        mensageiro.convertAndSendToUser(usuarioId.toString(), FILA_CHAT, evento);
    }
}
