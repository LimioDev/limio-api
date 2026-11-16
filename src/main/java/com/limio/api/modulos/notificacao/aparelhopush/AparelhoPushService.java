package com.limio.api.modulos.notificacao.aparelhopush;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.notificacao.enums.Plataforma;

/** Camada técnica de persistência de {@link AparelhoPush} — CRUD genérico (herdado) + finders da entity. */
@Component
public class AparelhoPushService extends BaseService<AparelhoPush, UUID, AparelhoPushRepository> {

    public AparelhoPushService(AparelhoPushRepository repository) {
        super(repository);
    }

    public Optional<AparelhoPush> buscarPorToken(String token) {
        return repository.findByToken(token);
    }

    public void registrar(UUID usuarioId, String token, Plataforma plataforma) {
        repository.registrar(usuarioId, token, plataforma.name());
    }

    public void remover(UUID usuarioId, String token) {
        repository.apagarTokenDoUsuario(usuarioId, token);
    }

    public List<String> tokensDoUsuario(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId).stream().map(AparelhoPush::getToken).toList();
    }

    /**
     * Tokens que o provedor disse que não existem mais (app desinstalado, permissão revogada). Transação própria pelo
     * mesmo motivo de {@code EnvioNotificacaoService.salvarTodos}: roda depois de um commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void apagarTokens(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            repository.apagarTokens(tokens);
        }
    }

    public void apagarDoUsuario(UUID usuarioId) {
        repository.apagarDoUsuario(usuarioId);
    }
}
