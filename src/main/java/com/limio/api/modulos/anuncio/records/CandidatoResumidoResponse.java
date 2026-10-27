package com.limio.api.modulos.anuncio.records;

import java.time.Instant;

/** Avatar de um candidato: {@code fotoUrl}/{@code fotoExpiraEm} {@code null} sem foto; aí o app mostra as iniciais. */
public record CandidatoResumidoResponse(String iniciais, String fotoUrl, Instant fotoExpiraEm) {
}
