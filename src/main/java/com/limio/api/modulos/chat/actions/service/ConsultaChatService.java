package com.limio.api.modulos.chat.actions.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaChat;
import com.limio.api.comum.portas.ResumoMensagem;
import com.limio.api.modulos.chat.Chat;
import com.limio.api.modulos.chat.ChatService;
import com.limio.api.modulos.chat.mensagem.MensagemService;

@Component
public class ConsultaChatService implements ConsultaChat {

    private final ChatService chatService;
    private final MensagemService mensagemService;

    public ConsultaChatService(ChatService chatService, MensagemService mensagemService) {
        this.chatService = chatService;
        this.mensagemService = mensagemService;
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

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumoMensagem> buscarMensagem(UUID mensagemId) {
        return mensagemService.buscarPorId(mensagemId)
                .flatMap(mensagem -> chatService.buscarPorId(mensagem.getChatId())
                        .map(chat -> new ResumoMensagem(mensagem.getId(), mensagem.getRemetenteId(),
                                chat.getAplicacaoId(), chat.getAnuncioId(), chat.getContratanteId(),
                                chat.getPrestadorId())));
    }
}
