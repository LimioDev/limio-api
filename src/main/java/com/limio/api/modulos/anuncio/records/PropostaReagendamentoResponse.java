package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

/**
 * Proposta de reagendamento ainda pendente de resposta, resumida dentro do serviço combinado (ETI-86). É o único lugar
 * onde o app enxerga a proposta pendente: o {@code id} abre {@code GET /reagendamentos/{id}} (ETI-99) e o
 * {@code proponenteId} diz se quem vê é a contraparte (responder) ou o proponente (cancelar a proposta).
 */
public record PropostaReagendamentoResponse(UUID id, UUID proponenteId, Instant dataProposta, String justificativa,
        Instant expiraEm) {
}
