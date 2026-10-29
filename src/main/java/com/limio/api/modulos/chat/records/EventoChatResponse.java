package com.limio.api.modulos.chat.records;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.limio.api.modulos.chat.enums.TipoEventoChat;

/**
 * O que chega em {@code /user/queue/chat}. {@code MENSAGEM_NOVA} traz {@code mensagem}; {@code ENTREGUE} e
 * {@code LIDA} trazem {@code mensagemIds} (as mensagens do remetente que a outra parte confirmou). O campo que não se
 * aplica vem {@code null}.
 */
public record EventoChatResponse(
        TipoEventoChat tipo,
        UUID chatId,
        MensagemResponse mensagem,
        List<UUID> mensagemIds,
        Instant em) {
}
