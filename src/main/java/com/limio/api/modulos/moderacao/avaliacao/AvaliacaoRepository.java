package com.limio.api.modulos.moderacao.avaliacao;

import java.util.UUID;

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
}
