package com.limio.api.modulos.anuncio.records;

import java.time.Instant;

import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

public record ServicoValidadoResponse(StatusAnuncio status, Instant validadoEm) {
}
