package com.limio.api.modulos.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface ChatRepository extends BaseRepository<Chat, UUID> {

    Optional<Chat> findByAplicacaoId(UUID aplicacaoId);

    List<Chat> findByAplicacaoIdIn(Collection<UUID> aplicacaoIds);

    List<Chat> findByAnuncioIdAndSomenteLeituraFalse(UUID anuncioId);

    @Query("""
            select c from Chat c
            where (c.contratanteId = :usuarioId or c.prestadorId = :usuarioId) and c.somenteLeitura = false
            """)
    List<Chat> buscarAbertosDoParticipante(@Param("usuarioId") UUID usuarioId);

    /** O índice único de {@code aplicacao_id} decide a corrida entre o listener e quem abre o chat. */
    @Modifying
    @Query(value = """
            insert into chat (aplicacao_id, anuncio_id, contratante_id, prestador_id)
            values (:aplicacaoId, :anuncioId, :contratanteId, :prestadorId)
            on conflict (aplicacao_id) do nothing
            """, nativeQuery = true)
    int inserirSeAusente(@Param("aplicacaoId") UUID aplicacaoId, @Param("anuncioId") UUID anuncioId,
            @Param("contratanteId") UUID contratanteId, @Param("prestadorId") UUID prestadorId);
}
