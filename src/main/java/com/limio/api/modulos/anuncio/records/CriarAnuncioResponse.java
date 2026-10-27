package com.limio.api.modulos.anuncio.records;

import java.util.UUID;

import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

public record CriarAnuncioResponse(UUID id, StatusAnuncio status) {
}
