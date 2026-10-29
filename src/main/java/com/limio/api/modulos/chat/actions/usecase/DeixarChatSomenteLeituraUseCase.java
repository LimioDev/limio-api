package com.limio.api.modulos.chat.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.eventos.AnuncioCanceladoEvent;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;

/**
 * Reação de {@code chat} ao fim da candidatura ou do anúncio (ETI-101): o chat para de receber mensagens e o histórico
 * continua. Roda depois do commit de quem publicou, em transação própria (ADR-0003).
 *
 * Só {@code AnuncioCanceladoEvent} existe hoje. Os cards de recusa (ETI-67), desistência (ETI-77), escolha (ETI-69) e
 * serviço cancelado (ETI-94/ETI-96) publicam seus eventos e ligam aqui um listener que chama
 * {@link #executarDaCandidatura} (ou {@link #executarDoAnuncio}) com o {@link MotivoSomenteLeitura} do cenário.
 */
@Service
public class DeixarChatSomenteLeituraUseCase {

    private final ChatService chatService;

    public DeixarChatSomenteLeituraUseCase(ChatService chatService) {
        this.chatService = chatService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCancelarAnuncio(AnuncioCanceladoEvent evento) {
        executarDoAnuncio(evento.anuncioId(), MotivoSomenteLeitura.ANUNCIO_CANCELADO, evento.ocorridoEm());
    }

    /** O chat de uma candidatura; sem chat (candidatura anterior ao módulo), nada a fazer. */
    @Transactional
    public void executarDaCandidatura(UUID aplicacaoId, MotivoSomenteLeitura motivo, Instant agora) {
        chatService.buscarDaCandidatura(aplicacaoId).ifPresent(chat -> {
            chat.deixarSomenteLeitura(motivo, agora);
            chatService.salvar(chat);
        });
    }

    /** Todos os chats ainda abertos de um anúncio. */
    @Transactional
    public void executarDoAnuncio(UUID anuncioId, MotivoSomenteLeitura motivo, Instant agora) {
        chatService.buscarAbertosDoAnuncio(anuncioId).forEach(chat -> {
            chat.deixarSomenteLeitura(motivo, agora);
            chatService.salvar(chat);
        });
    }
}
