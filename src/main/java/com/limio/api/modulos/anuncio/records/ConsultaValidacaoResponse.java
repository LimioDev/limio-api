package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.List;

/** Resposta de {@code GET /anuncios/{anuncioId}/validacao} (ETI-92). */
public record ConsultaValidacaoResponse(
        List<FotoCheckoutResponse> fotosCheckout,
        Instant checkoutEm,
        String nomePrestador) {
}
