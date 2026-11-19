package com.limio.api.modulos.notificacao.records;

import com.limio.api.comum.records.PageResponse;

/**
 * Resposta de {@code GET /notificacoes} (ETI-110): a página e o total de não lidas do usuário, que o app mostra no sino
 * (tela 13).
 */
public record NotificacoesResponse(PageResponse<NotificacaoResponse> notificacoes, long naoLidas) {
}
