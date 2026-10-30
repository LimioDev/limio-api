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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Aplicacao a where a.id = :id")
    Optional<Aplicacao> findByIdParaAtualizar(@Param("id") UUID id);
}
