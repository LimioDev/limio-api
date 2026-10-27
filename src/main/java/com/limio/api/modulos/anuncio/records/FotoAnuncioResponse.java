package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code url} é um link temporário de leitura, válido até {@code expiraEm}; ambos {@code null} se o storage não
 * respondeu.
 */
public record FotoAnuncioResponse(UUID id, String url, Instant expiraEm) {
}
