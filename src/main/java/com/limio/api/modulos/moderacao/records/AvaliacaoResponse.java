package com.limio.api.modulos.moderacao.records;

import java.time.Instant;
import java.util.UUID;

/** Confirmação de {@code POST /anuncios/{anuncioId}/avaliacoes} (telas 45c e 45d). {@code comentario} pode vir nulo. */
public record AvaliacaoResponse(UUID id, UUID anuncioId, int nota, String comentario, Instant publicadaEm) {
}
