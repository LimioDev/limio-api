package com.limio.api.modulos.notificacao.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.notificacao.enums.TipoNotificacao;

/**
 * Uma notificação na tela 43 (ETI-110). {@code lidaEm} nulo = não lida. {@code anuncioId} nulo = sem anúncio ligado;
 * com valor, o app abre o detalhe do anúncio ao tocar.
 */
public record NotificacaoResponse(
        UUID id,
        TipoNotificacao tipo,
        String titulo,
        String corpo,
        Instant enviadaEm,
        Instant lidaEm,
        UUID anuncioId) {
}
