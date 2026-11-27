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
import com.limio.api.modulos.anuncio.enums.CausaEncerramento;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

public interface AnuncioRepository extends BaseRepository<Anuncio, UUID> {

    @Query(value = """
            SELECT a.id FROM anuncio a
            WHERE a.status = 'PUBLICADO'
              AND a.data_hora_pretendida <= :agora
              AND NOT EXISTS (SELECT 1 FROM aplicacao ap WHERE ap.anuncio_id = a.id)
            ORDER BY a.data_hora_pretendida, a.id
            LIMIT :lote
            """, nativeQuery = true)
    List<UUID> buscarIdsParaExpirar(@Param("agora") Instant agora, @Param("lote") int lote);

    @Query(value = """
            SELECT a.id FROM anuncio a
            WHERE ((a.status IN ('PUBLICADO', 'EM_ANDAMENTO') AND a.data_hora_pretendida <= :limiteAgenda)
                OR (a.status = 'AGUARDANDO_VALIDACAO'
                    AND a.checkout_em + COALESCE(a.prazo_validacao_horas, :prazoValidacaoHoras) * INTERVAL '1 hour'
                        <= :agora))
              AND EXISTS (SELECT 1 FROM aplicacao ap WHERE ap.anuncio_id = a.id
                          AND ap.status IN ('ESCOLHIDA', 'AGUARDANDO_VALIDACAO'))
              AND NOT EXISTS (SELECT 1 FROM reagendamento r WHERE r.anuncio_id = a.id AND r.status = 'PENDENTE')
            ORDER BY COALESCE(a.checkout_em, a.data_hora_pretendida), a.id
            LIMIT :lote OFFSET :pular
            """, nativeQuery = true)
    List<UUID> buscarIdsParaConcluirPorSilencio(@Param("agora") Instant agora,
            @Param("limiteAgenda") Instant limiteAgenda, @Param("prazoValidacaoHoras") int prazoValidacaoHoras,
            @Param("lote") int lote, @Param("pular") int pular);

    /** Só os ids, de propósito: nada fica gerenciado antes da trava do anúncio (ver {@code AnuncioService}). */
    @Query("select a.id from Anuncio a where a.contratanteId = :contratanteId and a.status in :status")
    List<UUID> findIdsByContratanteIdAndStatusIn(@Param("contratanteId") UUID contratanteId,
            @Param("status") List<StatusAnuncio> status);

    List<Anuncio> findByContratanteIdAndStatusIn(UUID contratanteId, List<StatusAnuncio> status);

    /** Usa {@code idx_anuncio_contratante_status} — histórico resumido do perfil público (ETI-105). */
    long countByContratanteIdAndStatus(UUID contratanteId, StatusAnuncio status);

    List<Anuncio> findByContratanteIdAndStatusInOrderByDataHoraPretendidaAsc(UUID contratanteId,
            List<StatusAnuncio> status);

    /**
     * Filtros comuns do histórico (ETI-71). {@code status}: os status de anúncio em estado final aceitos;
     * {@code causas}: dentre os {@code ENCERRADO}, quais causas ({@code CONCLUIDO} não tem causa e não é filtrado
     * por ela; o banco garante que todo {@code ENCERRADO} tem uma).
     * Os dois nunca vão vazios. Período sobre a data do desfecho ({@link Anuncio#desfechoEm()}, aqui como
     * {@code coalesce(validadoEm, encerradoEm)}); os booleanos ligam/desligam
     * cada ponta (como no mural, evita parâmetro nulo tipado no Postgres).
     */
    String HISTORICO_FILTROS = """
            a.status in :status
            and (a.status = com.limio.api.modulos.anuncio.enums.StatusAnuncio.CONCLUIDO
                 or a.causaEncerramento in :causas)
            and (:semInicio = true or coalesce(a.validadoEm, a.encerradoEm) >= :de)
            and (:semFim = true or coalesce(a.validadoEm, a.encerradoEm) < :ate)
            """;

    String HISTORICO_ORDEM = "coalesce(a.validadoEm, a.encerradoEm) desc, a.id desc";

    /** Histórico do Empregador: os anúncios dele em estado final. O {@code pageable} não deve trazer {@code Sort}. */
    @Query(value = "select a from Anuncio a where a.contratanteId = :usuarioId and " + HISTORICO_FILTROS
            + " order by " + HISTORICO_ORDEM,
            countQuery = "select count(a) from Anuncio a where a.contratanteId = :usuarioId and "
                    + HISTORICO_FILTROS)
    Page<Anuncio> buscarHistoricoDoContratante(@Param("usuarioId") UUID usuarioId,
            @Param("status") Collection<StatusAnuncio> status, @Param("causas") Collection<CausaEncerramento> causas,
            @Param("semInicio") boolean semInicio, @Param("de") Instant de,
            @Param("semFim") boolean semFim, @Param("ate") Instant ate, Pageable pageable);

    /**
     * Histórico do Prestador: anúncios em estado final em que ele tem alguma candidatura. {@code EXISTS}, não join:
     * o mesmo Prestador pode ter várias candidaturas no mesmo anúncio e o anúncio só pode aparecer uma vez.
     * O {@code pageable} não deve trazer {@code Sort}.
     */
    @Query(value = "select a from Anuncio a where exists (select 1 from Aplicacao ap where ap.anuncioId = a.id "
            + "and ap.prestadorId = :usuarioId) and " + HISTORICO_FILTROS + " order by " + HISTORICO_ORDEM,
            countQuery = "select count(a) from Anuncio a where exists (select 1 from Aplicacao ap "
                    + "where ap.anuncioId = a.id and ap.prestadorId = :usuarioId) and " + HISTORICO_FILTROS)
    Page<Anuncio> buscarHistoricoDoPrestador(@Param("usuarioId") UUID usuarioId,
            @Param("status") Collection<StatusAnuncio> status, @Param("causas") Collection<CausaEncerramento> causas,
            @Param("semInicio") boolean semInicio, @Param("de") Instant de,
            @Param("semFim") boolean semFim, @Param("ate") Instant ate, Pageable pageable);

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
