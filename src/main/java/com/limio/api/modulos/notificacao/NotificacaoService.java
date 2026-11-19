package com.limio.api.modulos.notificacao;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link Notificacao} — CRUD genérico (herdado) + finders da entity. */
@Component
public class NotificacaoService extends BaseService<Notificacao, UUID, NotificacaoRepository> {

    public NotificacaoService(NotificacaoRepository repository) {
        super(repository);
    }

    public Page<Notificacao> buscarDoUsuario(UUID usuarioId, Pageable pageable) {
        return repository.findByUsuarioIdOrderByEnviadaEmDescIdDesc(usuarioId, pageable);
    }

    public Optional<Notificacao> buscarPorId(UUID notificacaoId) {
        return repository.findById(notificacaoId);
    }

    public long contarNaoLidas(UUID usuarioId) {
        return repository.countByUsuarioIdAndLidaEmIsNull(usuarioId);
    }

    /** Vazio também quando a notificação é de outro usuário: quem chama não distingue os dois casos. */
    public Optional<Notificacao> buscarDoUsuario(UUID notificacaoId, UUID usuarioId) {
        return repository.findByIdAndUsuarioId(notificacaoId, usuarioId);
    }

    public int marcarTodasComoLidas(UUID usuarioId, Instant agora) {
        return repository.marcarTodasComoLidas(usuarioId, agora);
    }

    public void apagarDoUsuario(UUID usuarioId) {
        repository.apagarDoUsuario(usuarioId);
    }
}
