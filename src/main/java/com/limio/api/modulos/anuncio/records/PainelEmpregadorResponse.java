package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.List;

/** {@code fotoUrl} é um link temporário de leitura: depois de {@code fotoExpiraEm}, consultar de novo. */
public record PainelEmpregadorResponse(
        String nomeExibicao,
        String fotoUrl,
        Instant fotoExpiraEm,
        int anunciosAbertos,
        int novosCandidatos,
        List<AnuncioPainelResponse> anuncios) {
}
