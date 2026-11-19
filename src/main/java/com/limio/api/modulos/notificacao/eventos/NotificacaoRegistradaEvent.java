package com.limio.api.modulos.notificacao.eventos;

import java.util.UUID;

/**
 * Uma notificação foi gravada pra lista do app (ETI-112). Interno ao {@code notificacao}: o {@code
 * EntregarNotificacaoUseCase} escuta em {@code AFTER_COMMIT} e envia pelos canais ligados, fora da transação que
 * gravou (ADR-0003: efeito externo só depois do commit).
 */
public record NotificacaoRegistradaEvent(UUID notificacaoId) {
}
