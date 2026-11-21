package com.limio.api.modulos.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Conversas do usuário, da última mensagem mais nova para a mais antiga; chat sem mensagem entra pela data de
     * criação. A última mensagem sai de um LATERAL (usa o índice de {@code chat_id, data_envio}) e as não lidas, de
     * uma subconsulta: nada disso vira consulta por chat. {@code :usuarioId} descarta as mensagens que ele mesmo
     * enviou.
     */
    String CONVERSAS = """
            SELECT c.id AS "chatId", c.aplicacao_id AS "candidaturaId", c.contratante_id AS "contratanteId",
                   c.prestador_id AS "prestadorId", c.somente_leitura AS "somenteLeitura",
                   c.motivo_somente_leitura AS "motivoSomenteLeitura", m.texto_exibido AS "ultimaMensagem",
                   m.data_envio AS "ultimaMensagemEm", m.remetente_id AS "ultimaMensagemRemetenteId",
                   (SELECT count(*) FROM mensagem n
                     WHERE n.chat_id = c.id AND n.remetente_id <> :usuarioId AND n.lida_em IS NULL) AS "naoLidas"
              FROM chat c
              LEFT JOIN LATERAL (
                   SELECT x.texto_exibido, x.data_envio, x.remetente_id FROM mensagem x
                    WHERE x.chat_id = c.id ORDER BY x.data_envio DESC, x.id DESC LIMIT 1) m ON TRUE
            """;

    String ORDEM_DAS_CONVERSAS = " ORDER BY COALESCE(m.data_envio, c.criado_em) DESC, c.id DESC";

    String COM_NAO_LIDAS = """
             AND EXISTS (SELECT 1 FROM mensagem n
                          WHERE n.chat_id = c.id AND n.remetente_id <> :usuarioId AND n.lida_em IS NULL)
            """;

    @Query(value = CONVERSAS + " WHERE c.contratante_id = :usuarioId" + ORDEM_DAS_CONVERSAS,
            countQuery = "SELECT count(*) FROM chat c WHERE c.contratante_id = :usuarioId", nativeQuery = true)
    Page<ConversaProjecao> listarDoContratante(@Param("usuarioId") UUID usuarioId, Pageable pageable);

    @Query(value = CONVERSAS + " WHERE c.prestador_id = :usuarioId" + ORDEM_DAS_CONVERSAS,
            countQuery = "SELECT count(*) FROM chat c WHERE c.prestador_id = :usuarioId", nativeQuery = true)
    Page<ConversaProjecao> listarDoPrestador(@Param("usuarioId") UUID usuarioId, Pageable pageable);

    @Query(value = "SELECT count(*) FROM chat c WHERE c.contratante_id = :usuarioId" + COM_NAO_LIDAS,
            nativeQuery = true)
    long contarDoContratanteComNaoLidas(@Param("usuarioId") UUID usuarioId);

    @Query(value = "SELECT count(*) FROM chat c WHERE c.prestador_id = :usuarioId" + COM_NAO_LIDAS,
            nativeQuery = true)
    long contarDoPrestadorComNaoLidas(@Param("usuarioId") UUID usuarioId);

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
