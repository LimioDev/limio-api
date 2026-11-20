package com.limio.api.modulos.anuncio.records;

import java.util.UUID;

import com.limio.api.comum.eventos.enums.TipoCancelamento;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

public record CancelamentoServicoResponse(
        UUID anuncioId,
        StatusAnuncio status,
        TipoCancelamento tipoCancelamento,
        boolean cotaEstornada) {
}
