package com.limio.api.modulos.anuncio.records;

import java.time.Instant;

import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

public record ServicoIniciadoResponse(StatusAnuncio status, Instant iniciadoEm) {
}
