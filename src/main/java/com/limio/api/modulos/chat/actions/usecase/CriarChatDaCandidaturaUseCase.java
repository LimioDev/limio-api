package com.limio.api.modulos.chat.actions.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.eventos.CandidaturaCriadaEvent;
import com.limio.api.modulos.chat.ChatService;

/**
 * Reação de {@code chat} à candidatura (ETI-101): abre o Chat 1:1 entre o Empregador e o Prestador, recebendo
 * mensagens. Sem candidatura não há chat.
 *
 * Roda depois do commit de {@code anuncio}, em transação própria (ADR-0003). Exceção aqui vira só log no Spring, por
 * isso o {@link AbrirChatUseCase} cria o chat que faltar. Evento repetido não cria um segundo chat: o índice único de
 * {@code aplicacao_id} decide.
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
        chatService.garantirDaCandidatura(evento.candidaturaId(), evento.anuncioId(), evento.contratanteId(),
                evento.prestadorId());
    }
}
