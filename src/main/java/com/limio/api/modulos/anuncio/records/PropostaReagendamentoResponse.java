package com.limio.api.modulos.anuncio.records;

import java.time.Instant;

/**
 * Proposta de reagendamento ainda pendente de resposta, resumida dentro do serviço combinado (ETI-86). Quem propôs,
 * o id e o prazo de resposta vêm de {@code ReagendamentoResponse} ({@code GET /reagendamentos/{id}}, ETI-99).
 */
public record PropostaReagendamentoResponse(Instant dataProposta, String justificativa) {
}
