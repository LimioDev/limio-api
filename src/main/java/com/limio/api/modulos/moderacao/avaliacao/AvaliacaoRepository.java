package com.limio.api.modulos.moderacao.avaliacao;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;
import com.limio.api.comum.seguranca.enums.PapelUsuario;

public interface AvaliacaoRepository extends BaseRepository<Avaliacao, UUID> {

    boolean existsByAnuncioIdAndAvaliadorId(UUID anuncioId, UUID avaliadorId);

    long countByAvaliadoIdAndPapelAvaliado(UUID avaliadoId, PapelUsuario papelAvaliado);

    /** {@code null} quando o usuário não tem avaliação nesse papel. */
    @Query("select avg(a.nota) from Avaliacao a where a.avaliadoId = :avaliadoId and a.papelAvaliado = :papel")
    Double mediaDasNotas(@Param("avaliadoId") UUID avaliadoId, @Param("papel") PapelUsuario papel);

    /** Mais recente primeiro; {@code pageable} limita (ex.: top 5 do perfil público, ETI-105). */
    List<Avaliacao> findByAvaliadoIdAndPapelAvaliadoOrderByPublicadaEmDesc(UUID avaliadoId, PapelUsuario papel,
            Pageable pageable);

    /**
     * Resumo (quantidade + média) de vários avaliados num papel, numa consulta só — usa
     * {@code idx_avaliacao_avaliado_papel} (ETI-67, candidatos do anúncio, sempre papel {@code PRESTADOR}).
     */
    @Query("""
            select a.avaliadoId as avaliadoId, avg(a.nota) as media, count(a) as qtd
            from Avaliacao a
            where a.avaliadoId in :avaliadoIds and a.papelAvaliado = :papel
            group by a.avaliadoId
            """)
    List<ResumoAvaliacaoProjecao> resumirPorAvaliados(@Param("avaliadoIds") Collection<UUID> avaliadoIds,
            @Param("papel") PapelUsuario papel);
}
