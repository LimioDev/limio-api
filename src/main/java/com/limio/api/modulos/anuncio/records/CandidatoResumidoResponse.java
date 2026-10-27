package com.limio.api.modulos.anuncio.records;

import java.time.Instant;

/**
 * Candidato no card da tela 16: {@code nome} abreviado ({@code "Rafael S."}); {@code fotoUrl}/{@code fotoExpiraEm}
 * {@code null} sem foto.
 */
public record CandidatoResumidoResponse(String nome, String fotoUrl, Instant fotoExpiraEm) {
}
