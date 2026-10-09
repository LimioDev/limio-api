package com.limio.api.modulos.notificacao.preferencianotificacao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link PreferenciaNotificacao} — CRUD genérico (herdado) + finder da entity. */
@Component
public class PreferenciaNotificacaoService
        extends BaseService<PreferenciaNotificacao, UUID, PreferenciaNotificacaoRepository> {

    public PreferenciaNotificacaoService(PreferenciaNotificacaoRepository repository) {
        super(repository);
    }

    public Optional<PreferenciaNotificacao> buscarPorUsuario(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }
}
