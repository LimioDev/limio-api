package com.limio.api.modulos.anuncio.records;

import java.time.Instant;

/**
 * Proposta de reagendamento ainda pendente de resposta (ETI-86). Sem {@code quemPropos} e {@code prazo}: a
 * tabela {@code reagendamento} (TICKET-0049) não guarda quem propôs nem o prazo de resposta — fica pro ETI-99,
 * que cria a proposta e decide o schema.
 */
public record PropostaReagendamentoResponse(Instant dataProposta, String justificativa) {
}
