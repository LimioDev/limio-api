package com.limio.api.modulos.perfil.midia;

import java.time.Instant;

/** Endereço assinado de leitura de um objeto do bucket privado, válido até {@code expiraEm}. */
public record LinkTemporario(String url, Instant expiraEm) {
}
