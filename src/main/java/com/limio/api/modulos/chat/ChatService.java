package com.limio.api.modulos.chat;

import java.util.Collection;
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

    public Optional<Chat> buscarDaCandidatura(UUID aplicacaoId) {
        return repository.findByAplicacaoId(aplicacaoId);
    }

    /** Chats que já existem entre as candidaturas. */
    public List<Chat> buscarDasCandidaturas(Collection<UUID> aplicacaoIds) {
        return repository.findByAplicacaoIdIn(aplicacaoIds);
    }

    /**
     * O chat da candidatura, criado agora se ainda não existe. Idempotente e seguro com chamadas simultâneas: quem
     * perde a corrida no índice único só lê o chat de quem ganhou.
     */
    public Chat garantirDaCandidatura(UUID aplicacaoId, UUID anuncioId, UUID contratanteId, UUID prestadorId) {
        return repository.findByAplicacaoId(aplicacaoId).orElseGet(() -> {
            repository.inserirSeAusente(aplicacaoId, anuncioId, contratanteId, prestadorId);
            return repository.findByAplicacaoId(aplicacaoId).orElseThrow();
        });
    }

    /** Chats do anúncio que ainda recebem mensagens. */
    public List<Chat> buscarAbertosDoAnuncio(UUID anuncioId) {
        return repository.findByAnuncioIdAndSomenteLeituraFalse(anuncioId);
    }

    /** Chats em que o usuário é uma das partes e que ainda recebem mensagens. */
    public List<Chat> buscarAbertosDoParticipante(UUID usuarioId) {
        return repository.buscarAbertosDoParticipante(usuarioId);
    }
}
