package com.limio.api.modulos.anuncio.records;

import java.util.UUID;

/** Resposta da escolha do prestador (ETI-69): o id do anúncio, que passa a representar o serviço combinado. */
public record EscolhaResponse(UUID anuncioId) {
}
