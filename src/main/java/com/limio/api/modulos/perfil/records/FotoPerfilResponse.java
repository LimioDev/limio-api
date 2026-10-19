package com.limio.api.modulos.perfil.records;

import java.time.Instant;

/** {@code url} é um link temporário de leitura: depois de {@code expiraEm}, consultar de novo. */
public record FotoPerfilResponse(String url, Instant expiraEm) {
}
