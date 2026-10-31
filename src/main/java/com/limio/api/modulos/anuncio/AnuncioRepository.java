package com.limio.api.modulos.anuncio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

import jakarta.persistence.LockModeType;

public interface AnuncioRepository extends BaseRepository<Anuncio, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Anuncio a where a.id = :id")
    Optional<Anuncio> findByIdParaAtualizar(UUID id);

    List<Anuncio> findByContratanteIdAndStatusIn(UUID contratanteId, List<StatusAnuncio> status);

    List<Anuncio> findByContratanteIdAndStatusInOrderByDataHoraPretendidaAsc(UUID contratanteId,
            List<StatusAnuncio> status);
}
