package com.limio.api.modulos.chat.actions.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaChat;
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;

@Component
public class ConsultaChatService implements ConsultaChat {

    private final ChatService chatService;

    public ConsultaChatService(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> buscarIdsPorCandidatura(Collection<UUID> candidaturaIds) {
        if (candidaturaIds.isEmpty()) {
            return Map.of();
        }
        return chatService.buscarDasCandidaturas(candidaturaIds).stream()
                .collect(Collectors.toMap(Chat::getAplicacaoId, Chat::getId));
    }
}
