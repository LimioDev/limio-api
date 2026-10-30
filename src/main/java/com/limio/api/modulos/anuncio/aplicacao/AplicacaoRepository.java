package com.limio.api.modulos.anuncio.aplicacao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

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

    Optional<Aplicacao> findFirstByAnuncioIdAndPrestadorIdOrderByDataAplicacaoDescIdDesc(UUID anuncioId,
            UUID prestadorId);
}
