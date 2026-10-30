package com.limio.api.modulos.anuncio;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

public interface AnuncioRepository extends BaseRepository<Anuncio, UUID> {

    List<Anuncio> findByContratanteIdAndStatusIn(UUID contratanteId, List<StatusAnuncio> status);

    List<Anuncio> findByContratanteIdAndStatusInOrderByDataHoraPretendidaAsc(UUID contratanteId,
            List<StatusAnuncio> status);

    /**
     * Anúncios candidatos do mural com a distância (haversine, km) até a base do Prestador. A caixa
     * envolvente (lat/lon) corta antes do cálculo e usa {@code idx_anuncio_mural_geo}. Ordem: mais perto,
     * depois data, depois id (aliases entre aspas: a projeção casa por nome camelCase) (estável entre páginas). Categorias de filtro nunca vazias aqui: o service
     * liga {@code semFiltroCategoria} e manda um valor qualquer.
     */
    String MURAL_CANDIDATOS = """
            SELECT a.id AS id, a.titulo AS titulo, a.categoria_id AS "categoriaId",
                   a.contratante_id AS "contratanteId", a.data_hora_pretendida AS "dataHoraPretendida",
                   a.bairro_regiao_snapshot AS "bairroRegiao",
                   2 * 6371.0 * asin(sqrt(least(1.0,
                       power(sin(radians(a.latitude_snapshot - :latitude) / 2), 2)
                       + cos(radians(:latitude)) * cos(radians(a.latitude_snapshot))
                         * power(sin(radians(a.longitude_snapshot - :longitude) / 2), 2)))) AS "distanciaKm"
            FROM anuncio a
            WHERE a.status = 'PUBLICADO'
              AND a.contratante_id <> :prestadorId
              AND a.latitude_snapshot BETWEEN :latitudeMin AND :latitudeMax
              AND a.longitude_snapshot BETWEEN :longitudeMin AND :longitudeMax
              AND a.data_hora_pretendida > :de
              AND (:semLimite OR a.data_hora_pretendida < :ate)
              AND (a.categoria_id IN (:especialidades) OR a.categoria_id = :categoriaOutros OR :possuiOutros)
              AND (:semFiltroCategoria OR a.categoria_id IN (:categoriasFiltro))
              AND NOT EXISTS (SELECT 1 FROM aplicacao ap
                              WHERE ap.anuncio_id = a.id AND ap.status = 'ESCOLHIDA')
            """;

    @Query(value = "SELECT * FROM (" + MURAL_CANDIDATOS + ") m WHERE m.\"distanciaKm\" <= :raioKm "
            + "ORDER BY m.\"distanciaKm\", m.\"dataHoraPretendida\", m.id",
            countQuery = "SELECT count(*) FROM (" + MURAL_CANDIDATOS + ") m WHERE m.\"distanciaKm\" <= :raioKm",
            nativeQuery = true)
    Page<AnuncioDoMuralProjecao> buscarMural(
            @Param("prestadorId") UUID prestadorId,
            @Param("especialidades") Collection<UUID> especialidades,
            @Param("categoriaOutros") UUID categoriaOutros,
            @Param("possuiOutros") boolean possuiOutros,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("latitudeMin") double latitudeMin,
            @Param("latitudeMax") double latitudeMax,
            @Param("longitudeMin") double longitudeMin,
            @Param("longitudeMax") double longitudeMax,
            @Param("raioKm") double raioKm,
            @Param("semFiltroCategoria") boolean semFiltroCategoria,
            @Param("categoriasFiltro") Collection<UUID> categoriasFiltro,
            @Param("de") Instant de,
            @Param("semLimite") boolean semLimite,
            @Param("ate") Instant ate,
            Pageable pageable);
}
