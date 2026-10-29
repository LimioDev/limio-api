package com.limio.api.comum.portas;

import java.time.Instant;

/** Link temporário de leitura da foto de perfil, válido até {@code expiraEm}. */
public record FotoPerfil(String url, Instant expiraEm) {
}
