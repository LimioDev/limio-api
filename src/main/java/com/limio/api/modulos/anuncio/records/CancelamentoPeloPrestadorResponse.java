package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

public record CancelamentoPeloPrestadorResponse(
        UUID anuncioId,
        StatusAnuncio status,
        Instant encerradoEm) {
}
