package com.limio.api.modulos.chat.actions.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.eventos.CandidaturaCriadaEvent;
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;

/**
 * Reação de {@code chat} à candidatura (ETI-101): abre o Chat 1:1 entre o Empregador e o Prestador, recebendo
 * mensagens. Sem candidatura não há chat: esta é a única porta de criação.
 *
 * Roda depois do commit de {@code anuncio}, em transação própria (ADR-0003). Evento repetido não cria um segundo
 * chat: a checagem cobre o caso normal e o índice único de {@code aplicacao_id} cobre a corrida.
 */
@Service
public class CriarChatDaCandidaturaUseCase {

    private final ChatService chatService;

    public CriarChatDaCandidaturaUseCase(ChatService chatService) {
        this.chatService = chatService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoCriarCandidatura(CandidaturaCriadaEvent evento) {
        if (chatService.existeDaCandidatura(evento.candidaturaId()))
            return;
        chatService.salvar(Chat.daCandidatura(evento.candidaturaId(), evento.anuncioId(), evento.contratanteId(),
                evento.prestadorId()));
    }
}
