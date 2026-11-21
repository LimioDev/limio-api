package com.limio.api.modulos.notificacao.envionotificacao;

import java.util.List;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface EnvioNotificacaoRepository extends BaseRepository<EnvioNotificacao, UUID> {

    List<EnvioNotificacao> findByNotificacaoId(UUID notificacaoId);
}
