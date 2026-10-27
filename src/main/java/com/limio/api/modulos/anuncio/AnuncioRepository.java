package com.limio.api.modulos.anuncio;

import java.util.List;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

public interface AnuncioRepository extends BaseRepository<Anuncio, UUID> {

    List<Anuncio> findByContratanteIdAndStatusIn(UUID contratanteId, List<StatusAnuncio> status);

    List<Anuncio> findByContratanteIdAndStatusInOrderByDataHoraPretendidaAsc(UUID contratanteId,
            List<StatusAnuncio> status);
}
