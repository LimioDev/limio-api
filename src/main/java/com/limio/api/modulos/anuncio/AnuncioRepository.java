package com.limio.api.modulos.anuncio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

import jakarta.persistence.LockModeType;

public interface AnuncioRepository extends BaseRepository<Anuncio, UUID> {

    List<Anuncio> findByContratanteIdAndStatusIn(UUID contratanteId, List<StatusAnuncio> status);

    List<Anuncio> findByContratanteIdAndStatusInOrderByDataHoraPretendidaAsc(UUID contratanteId,
            List<StatusAnuncio> status);

    /** Mesma trava de {@code UsuarioRepository.findByIdParaAtualizar}: a segunda requisição espera a primeira. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Anuncio a where a.id = :id")
    Optional<Anuncio> findByIdParaAtualizar(@Param("id") UUID id);
}
