package com.limio.api.modulos.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.chat.excecao.ChatNaoEncontradoException;

/** Camada técnica de persistência de {@link Chat} — CRUD genérico (herdado) + finders da entity. */
@Component
public class ChatService extends BaseService<Chat, UUID, ChatRepository> {

    public ChatService(ChatRepository repository) {
        super(repository);
    }

    /** Chat de quem é parte dele. Para terceiro responde igual a um chat que não existe, sem revelar que existe. */
    public Chat buscarDoParticipante(UUID chatId, UUID usuarioId) {
        return repository.findById(chatId)
                .filter(chat -> chat.participa(usuarioId))
                .orElseThrow(ChatNaoEncontradoException::new);
    }

    /** Mesma regra de {@link #buscarDoParticipante}, pela candidatura. */
    public Chat buscarDaCandidaturaDoParticipante(UUID aplicacaoId, UUID usuarioId) {
        return repository.findByAplicacaoId(aplicacaoId)
                .filter(chat -> chat.participa(usuarioId))
                .orElseThrow(ChatNaoEncontradoException::new);
    }

    public Optional<Chat> buscarDaCandidatura(UUID aplicacaoId) {
        return repository.findByAplicacaoId(aplicacaoId);
    }

    public boolean existeDaCandidatura(UUID aplicacaoId) {
        return repository.existsByAplicacaoId(aplicacaoId);
    }

    /** Chats do anúncio que ainda recebem mensagens. */
    public List<Chat> buscarAbertosDoAnuncio(UUID anuncioId) {
        return repository.findByAnuncioIdAndSomenteLeituraFalse(anuncioId);
    }
}
