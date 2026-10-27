package com.limio.api.comum.midia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.comum.midia.enums.TipoMidia;

public interface MidiaRepository extends BaseRepository<Midia, UUID> {

    Optional<Midia> findByUsuarioIdAndTipoAndExpurgoEmIsNull(UUID usuarioId, TipoMidia tipo);

    List<Midia> findByAnuncioIdAndTipoAndExpurgoEmIsNullOrderByCriadoEmAscIdAsc(UUID anuncioId, TipoMidia tipo);
}
