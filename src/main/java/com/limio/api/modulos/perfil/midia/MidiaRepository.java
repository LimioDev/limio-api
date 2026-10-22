package com.limio.api.modulos.perfil.midia;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.perfil.midia.enums.TipoMidia;

public interface MidiaRepository extends BaseRepository<Midia, UUID> {

    Optional<Midia> findByUsuarioIdAndTipoAndExpurgoEmIsNull(UUID usuarioId, TipoMidia tipo);
}
