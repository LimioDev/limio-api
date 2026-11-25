package com.limio.api.modulos.moderacao.denuncia;

import java.util.List;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface SancaoRepository extends BaseRepository<Sancao, UUID> {

    List<Sancao> findByUsuarioIdOrderByAplicadaEmDesc(UUID usuarioId);

    List<Sancao> findByDecisaoId(UUID decisaoId);
}
