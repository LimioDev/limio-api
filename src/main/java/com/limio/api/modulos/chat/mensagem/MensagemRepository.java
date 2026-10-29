package com.limio.api.modulos.chat.mensagem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

public interface MensagemRepository extends BaseRepository<Mensagem, UUID> {

    Slice<Mensagem> findByChatIdOrderByDataEnvioDescIdDesc(UUID chatId, Pageable pagina);

    /**
     * Mensagens mais antigas que {@code antesDe}, pela posição dela ({@code dataEnvio}, {@code id}) e não por número de
     * página: mensagem nova chegando no meio da rolagem não repete nem pula nada. Referência de outro chat devolve
     * vazio.
     */
    @Query("""
            select m from Mensagem m, Mensagem ref
            where ref.id = :antesDe and ref.chatId = :chatId and m.chatId = :chatId
              and (m.dataEnvio < ref.dataEnvio or (m.dataEnvio = ref.dataEnvio and m.id < ref.id))
            order by m.dataEnvio desc, m.id desc
            """)
    Slice<Mensagem> buscarAntesDe(@Param("chatId") UUID chatId, @Param("antesDe") UUID antesDe, Pageable pagina);

    /**
     * Marca como entregues, numa ida ao banco, as mensagens que a outra parte mandou para {@code usuarioId}. A trava de
     * linha do UPDATE faz a chamada simultânea não achar nada: cada id volta uma vez só.
     */
    @Query(value = """
            update mensagem set entregue_em = :agora, atualizado_em = :agora
            where chat_id = :chatId and remetente_id <> :usuarioId and entregue_em is null
            returning id
            """, nativeQuery = true)
    List<UUID> marcarEntregues(@Param("chatId") UUID chatId, @Param("usuarioId") UUID usuarioId,
            @Param("agora") Instant agora);

    /** Igual a {@link #marcarEntregues}, para leitura. Lida implica entregue: preenche a entrega que faltar. */
    @Query(value = """
            update mensagem set lida_em = :agora, entregue_em = coalesce(entregue_em, :agora), atualizado_em = :agora
            where chat_id = :chatId and remetente_id <> :usuarioId and lida_em is null
            returning id
            """, nativeQuery = true)
    List<UUID> marcarLidas(@Param("chatId") UUID chatId, @Param("usuarioId") UUID usuarioId,
            @Param("agora") Instant agora);
}
