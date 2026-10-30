package com.limio.api.modulos.anuncio.records;

import java.util.UUID;

import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

public record CandidaturaResponse(UUID id, StatusAplicacao status) {
}
