package com.limio.api.modulos.chat.records;

import com.limio.api.comum.records.PageResponse;

/**
 * Resposta de {@code GET /chats} (ETI-103). {@code totalComNaoLidas} conta as conversas do papel ativo com mensagem não
 * lida em todas as páginas.
 */
public record ConversasResponse(PageResponse<ConversaResponse> conversas, long totalComNaoLidas) {
}
