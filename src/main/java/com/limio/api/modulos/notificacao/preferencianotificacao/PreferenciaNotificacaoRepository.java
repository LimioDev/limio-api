package com.limio.api.modulos.notificacao.preferencianotificacao;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface PreferenciaNotificacaoRepository extends BaseRepository<PreferenciaNotificacao, UUID> {

    Optional<PreferenciaNotificacao> findByUsuarioId(UUID usuarioId);
}
