package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Algo aconteceu num anúncio/candidatura que a outra parte precisa saber
 * (TICKET-0049). {@code assunto} é texto livre em vez de um enum de {@code
 * TipoNotificacao} — esse enum é de {@code modulos.notificacao}, que {@code
 * anuncio} não pode importar (ADR-0001). Quando {@code notificacao} for
 * implementado, o listener decide o {@code TipoNotificacao}/canal a partir
 * do {@code assunto}; até lá ninguém escuta este evento (publicar antes do
 * consumidor existir não quebra nada — é só um evento sem listener).
 */
public record AvisoDeAnuncioEvent(UUID destinatarioId, UUID anuncioId, String assunto, Instant ocorridoEm) {
}
