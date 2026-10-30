package com.limio.api.modulos.chat.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.eventos.MensagemEnviadaEvent;
import com.limio.api.modulos.chat.excecao.ChatSomenteLeituraException;
import com.limio.api.modulos.chat.mensagem.Mensagem;
import com.limio.api.modulos.chat.mensagem.MensagemService;

/**
 * Grava a mensagem de uma das partes (ETI-101). Chat somente leitura recusa o envio. A entrega em tempo real à outra
 * parte sai depois do commit ({@code ChatTempoRealService}). Valor combinado aqui é só texto: não vira cobrança.
 */
@Service
public class EnviarMensagemUseCase {

    private final ChatService chatService;
    private final MensagemService mensagemService;
    private final ApplicationEventPublisher eventos;

    public EnviarMensagemUseCase(ChatService chatService, MensagemService mensagemService,
            ApplicationEventPublisher eventos) {
        this.chatService = chatService;
        this.mensagemService = mensagemService;
        this.eventos = eventos;
    }

    @Transactional
    public Mensagem executar(UUID chatId, UUID remetenteId, String texto) {
        Chat chat = chatService.buscarDoParticipante(chatId, remetenteId);
        if (chat.isSomenteLeitura())
            throw new ChatSomenteLeituraException();
        Mensagem mensagem = mensagemService.salvar(Mensagem.enviada(chatId, remetenteId, texto, Instant.now()));
        eventos.publishEvent(new MensagemEnviadaEvent(chat.outraParte(remetenteId), mensagem));
        return mensagem;
    }
}
