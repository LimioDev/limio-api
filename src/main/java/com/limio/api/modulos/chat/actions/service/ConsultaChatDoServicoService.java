package com.limio.api.modulos.chat.actions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaChatDoServico;
import com.limio.api.comum.portas.ResumoChatDoServico;
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.mensagem.MensagemService;

@Component
public class ConsultaChatDoServicoService implements ConsultaChatDoServico {

    private final ChatService chatService;
    private final MensagemService mensagemService;

    public ConsultaChatDoServicoService(ChatService chatService, MensagemService mensagemService) {
        this.chatService = chatService;
        this.mensagemService = mensagemService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumoChatDoServico> buscar(UUID candidaturaId, UUID usuarioId) {
        return chatService.buscarDaCandidatura(candidaturaId)
                .map(chat -> new ResumoChatDoServico(chat.getId(), naoLidas(chat, usuarioId)));
    }

    private int naoLidas(Chat chat, UUID usuarioId) {
        return mensagemService.contarNaoLidas(chat.getId(), usuarioId);
    }
}
