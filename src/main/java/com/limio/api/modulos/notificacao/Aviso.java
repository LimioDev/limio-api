package com.limio.api.modulos.notificacao;

import java.util.UUID;

import com.limio.api.modulos.notificacao.enums.TipoNotificacao;

/**
 * Um aviso pronto pra virar {@link Notificacao} (ETI-112): pra quem, de que tipo e com qual texto. Montado a partir
 * de um evento de domínio pelo {@code AvisoHelper}.
 *
 * @param anuncioId anúncio ligado, ou nulo
 */
public record Aviso(UUID destinatarioId, TipoNotificacao tipo, String titulo, String corpo, UUID anuncioId) {
}
