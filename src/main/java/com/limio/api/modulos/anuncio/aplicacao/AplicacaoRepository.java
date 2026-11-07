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

    /** @param fimExclusivo limite superior do período, não incluso */
    List<Aplicacao> findByPrestadorIdAndEscolhidaEmGreaterThanEqualAndEscolhidaEmLessThanOrderByEscolhidaEmDesc(
            UUID prestadorId, Instant inicio, Instant fimExclusivo);

    List<Aplicacao> findByAnuncioIdAndStatusIn(UUID anuncioId, List<StatusAplicacao> status);

    List<Aplicacao> findByAnuncioIdInAndStatusIn(List<UUID> anuncioIds, List<StatusAplicacao> status);

    boolean existsByAnuncioIdAndStatusIn(UUID anuncioId, List<StatusAplicacao> status);

    List<Aplicacao> findByAnuncioIdAndStatusOrderByDataAplicacaoAscIdAsc(UUID anuncioId, StatusAplicacao status);

    boolean existsByAnuncioIdAndPrestadorIdAndStatusIn(UUID anuncioId, UUID prestadorId, List<StatusAplicacao> status);

    /** @param fimExclusivo limite superior do período, não contado (início do mês seguinte) */
    long countByPrestadorIdAndEscolhidaEmGreaterThanEqualAndEscolhidaEmLessThan(UUID prestadorId, Instant inicio,
            Instant fimExclusivo);

    Page<Aplicacao> findByPrestadorIdAndStatusIn(UUID prestadorId, List<StatusAplicacao> status, Pageable pageable);

    /**
     * As candidaturas {@code ENVIADA} do anúncio, travadas ({@code FOR UPDATE}) em ordem de id: a escolha as muda
     * todas de uma vez, e a ordem fixa evita deadlock entre duas transações que travem o mesmo conjunto.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aplicacao a where a.anuncioId = :anuncioId and a.status = "
            + "com.limio.api.modulos.anuncio.enums.StatusAplicacao.ENVIADA order by a.id")
    List<Aplicacao> findEnviadasDoAnuncioParaAtualizar(@Param("anuncioId") UUID anuncioId);

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
