package com.limio.api.modulos.chat.actions.usecase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.eventos.AnuncioCanceladoEvent;
import com.limio.api.comum.eventos.CandidaturaRecusadaEvent;
import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;

/**
 * Reação de {@code chat} ao fim da candidatura, do anúncio ou da conta de uma das partes (ETI-101): o chat para de
 * receber mensagens e o histórico continua. Roda depois do commit de quem publicou, em transação própria (ADR-0003).
 *
 * Hoje escuta {@code AnuncioCanceladoEvent}, {@code CandidaturaRecusadaEvent} e {@code ContaEncerradaEvent}. Os cards
 * de desistência (ETI-77), escolha (ETI-69) e serviço cancelado (ETI-94/ETI-96) publicam seus eventos e ligam aqui um
 * listener que chama {@link #executarDaCandidatura} (ou {@link #executarDoAnuncio}) com o {@link MotivoSomenteLeitura}
 * do cenário.
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

    /** Recusada pelo Empregador (ETI-67): o histórico fica, o Prestador não recebe nem manda mais mensagem. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoRecusarCandidatura(CandidaturaRecusadaEvent evento) {
        executarDaCandidatura(evento.candidaturaId(), MotivoSomenteLeitura.CANDIDATURA_RECUSADA, evento.ocorridoEm());
    }

    /** A outra parte não fica mandando mensagem para "Usuário excluído". */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEncerrarConta(ContaEncerradaEvent evento) {
        deixarSomenteLeitura(chatService.buscarAbertosDoParticipante(evento.usuarioId()),
                MotivoSomenteLeitura.CONTA_ENCERRADA, evento.ocorridoEm());
    }

    /** O chat de uma candidatura; sem chat (candidatura anterior ao módulo), nada a fazer. */
    @Transactional
    public void executarDaCandidatura(UUID aplicacaoId, MotivoSomenteLeitura motivo, Instant agora) {
        deixarSomenteLeitura(chatService.buscarDaCandidatura(aplicacaoId).stream().toList(), motivo, agora);
    }

    /** Todos os chats ainda abertos de um anúncio. */
    @Transactional
    public void executarDoAnuncio(UUID anuncioId, MotivoSomenteLeitura motivo, Instant agora) {
        deixarSomenteLeitura(chatService.buscarAbertosDoAnuncio(anuncioId), motivo, agora);
    }

    private void deixarSomenteLeitura(List<Chat> chats, MotivoSomenteLeitura motivo, Instant agora) {
        chats.forEach(chat -> {
            chat.deixarSomenteLeitura(motivo, agora);
            chatService.salvar(chat);
        });
    }
}
