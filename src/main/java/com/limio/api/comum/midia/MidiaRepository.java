package com.limio.api.comum.midia;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.comum.midia.enums.TipoMidia;

public interface MidiaRepository extends BaseRepository<Midia, UUID> {

    Optional<Midia> findByUsuarioIdAndTipoAndExpurgoEmIsNull(UUID usuarioId, TipoMidia tipo);

    List<Midia> findByUsuarioIdInAndTipoAndExpurgoEmIsNull(Collection<UUID> usuarioIds, TipoMidia tipo);

    List<Midia> findByAnuncioIdAndTipoAndExpurgoEmIsNullOrderByCriadoEmAscIdAsc(UUID anuncioId, TipoMidia tipo);

    @Query(value = """
            SELECT DISTINCT ON (m.anuncio_id) m.*
            FROM midia m
            WHERE m.anuncio_id IN (:anuncioIds) AND m.tipo = 'FOTO_ANUNCIO' AND m.expurgo_em IS NULL
            ORDER BY m.anuncio_id, m.criado_em, m.id
            """, nativeQuery = true)
    List<Midia> findPrimeirasFotosDosAnuncios(@Param("anuncioIds") Collection<UUID> anuncioIds);
}
