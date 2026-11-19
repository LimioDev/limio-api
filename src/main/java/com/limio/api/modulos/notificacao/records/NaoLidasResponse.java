package com.limio.api.modulos.notificacao.records;

/**
 * Confirmação de "marcar como lida" e "marcar todas como lidas" (ETI-110): o total de não lidas depois da marcação,
 * para o app atualizar o sino sem listar de novo.
 */
public record NaoLidasResponse(long naoLidas) {
}
