package com.limio.api.modulos.anuncio.aplicacao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

import jakarta.persistence.LockModeType;

public interface AplicacaoRepository extends BaseRepository<Aplicacao, UUID> {

    List<Aplicacao> findByPrestadorIdAndStatusIn(UUID prestadorId, List<StatusAplicacao> status);

    /**
     * As escolhas do Prestador no período ({@code escolhidaEm} em {@code [inicio, fimExclusivo)}), em qualquer status,
     * da mais recente pra mais antiga, sem as que já tiveram a cota estornada: só as que ainda ocupam a cota (ETI-94).
     */
    @Query("select a from Aplicacao a where a.prestadorId = :prestadorId and a.escolhidaEm >= :inicio "
            + "and a.escolhidaEm < :fimExclusivo and a.cotaEstornadaEm is null order by a.escolhidaEm desc")
    List<Aplicacao> buscarEscolhidasQueContamNaCota(@Param("prestadorId") UUID prestadorId,
            @Param("inicio") Instant inicio, @Param("fimExclusivo") Instant fimExclusivo);

    List<Aplicacao> findByAnuncioIdAndStatusIn(UUID anuncioId, List<StatusAplicacao> status);

    /** Com os status do serviço vem no máximo uma: só a candidatura escolhida chega a eles. */
    Optional<Aplicacao> findFirstByAnuncioIdAndStatusIn(UUID anuncioId, List<StatusAplicacao> status);

    List<Aplicacao> findByAnuncioIdInAndStatusIn(List<UUID> anuncioIds, List<StatusAplicacao> status);

    boolean existsByAnuncioIdAndStatusIn(UUID anuncioId, List<StatusAplicacao> status);

    List<Aplicacao> findByAnuncioIdAndStatusOrderByDataAplicacaoAscIdAsc(UUID anuncioId, StatusAplicacao status);

    boolean existsByAnuncioIdAndPrestadorIdAndStatusIn(UUID anuncioId, UUID prestadorId, List<StatusAplicacao> status);

    /** Mesmo critério de {@link #buscarEscolhidasQueContamNaCota}, só a contagem. */
    @Query("select count(a) from Aplicacao a where a.prestadorId = :prestadorId and a.escolhidaEm >= :inicio "
            + "and a.escolhidaEm < :fimExclusivo and a.cotaEstornadaEm is null")
    long contarEscolhidasQueContamNaCota(@Param("prestadorId") UUID prestadorId, @Param("inicio") Instant inicio,
            @Param("fimExclusivo") Instant fimExclusivo);

    Page<Aplicacao> findByPrestadorIdAndStatusIn(UUID prestadorId, List<StatusAplicacao> status, Pageable pageable);

    /**
     * As candidaturas {@code ENVIADA} do anúncio, travadas ({@code FOR UPDATE}) em ordem de id: a escolha as muda
     * todas de uma vez, e a ordem fixa evita deadlock entre duas transações que travem o mesmo conjunto.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aplicacao a where a.anuncioId = :anuncioId and a.status = "
            + "com.limio.api.modulos.anuncio.enums.StatusAplicacao.ENVIADA order by a.id")
    List<Aplicacao> findEnviadasDoAnuncioParaAtualizar(@Param("anuncioId") UUID anuncioId);

    /** A candidatura {@code ESCOLHIDA} do anúncio, travada ({@code FOR UPDATE}); no máximo uma (V13). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aplicacao a where a.anuncioId = :anuncioId and a.status = "
            + "com.limio.api.modulos.anuncio.enums.StatusAplicacao.ESCOLHIDA")
    Optional<Aplicacao> findEscolhidaDoAnuncioParaAtualizar(@Param("anuncioId") UUID anuncioId);

    /** Consulta escalar: não carrega a entidade no contexto de persistência (ver {@code buscarAnuncioId}). */
    @Query("select a.anuncioId from Aplicacao a where a.id = :id")
    Optional<UUID> findAnuncioIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aplicacao a where a.id = :id")
    Optional<Aplicacao> findByIdParaAtualizar(@Param("id") UUID id);

    Optional<Aplicacao> findFirstByAnuncioIdAndPrestadorIdOrderByDataAplicacaoDescIdDesc(UUID anuncioId,
            UUID prestadorId);

    /** {@code escolhidaEm} nunca é limpo, mesmo depois do serviço concluir ou cancelar (ETI-86). */
    Optional<Aplicacao> findFirstByAnuncioIdAndEscolhidaEmIsNotNull(UUID anuncioId);
}
