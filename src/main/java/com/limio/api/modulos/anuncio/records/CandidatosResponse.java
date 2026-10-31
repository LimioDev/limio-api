package com.limio.api.modulos.anuncio.records;

import java.util.List;

/** Lista de candidatos do anúncio (ETI-67); {@code candidatos} vazio quando ninguém se candidatou. */
public record CandidatosResponse(List<CandidatoResponse> candidatos) {
}
