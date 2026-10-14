package com.limio.api.modulos.auth.sessao;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link Sessao} — CRUD genérico (herdado) + finders da entity. */
@Component
public class SessaoService extends BaseService<Sessao, UUID, SessaoRepository> {

    public SessaoService(SessaoRepository repository) {
        super(repository);
    }

    /** Trava a linha até o fim da transação de quem chama (renovação com rotação). */
    public Optional<Sessao> buscarPorRefreshTokenHashParaRenovar(String refreshTokenHash) {
        return repository.findByRefreshTokenHash(refreshTokenHash);
    }

    public Optional<Sessao> buscarDoUsuarioPorRefreshTokenHash(UUID usuarioId, String refreshTokenHash) {
        return repository.findByRefreshTokenHashAndUsuarioId(refreshTokenHash, usuarioId);
    }

    /**
     * Revoga todas as sessões ativas do usuário, exceto {@code exceto} (o
     * aparelho atual, na alteração de senha logada). {@code exceto} nulo
     * revoga todas sem excluir nenhuma (redefinição pelo link — não há
     * "aparelho atual" nesse fluxo).
     */
    public void revogarTodas(UUID usuarioId, UUID exceto, Instant agora) {
        repository.findByUsuarioIdAndRevogadaEmIsNull(usuarioId).stream()
                .filter(sessao -> exceto == null || !sessao.getId().equals(exceto))
                .forEach(sessao -> {
                    sessao.setRevogadaEm(agora);
                    repository.save(sessao);
                });
    }
}
