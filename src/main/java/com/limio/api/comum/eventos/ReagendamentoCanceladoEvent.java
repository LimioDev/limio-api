package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O proponente cancelou a própria proposta de reagendamento antes da resposta (ETI-99). A data vigente não mudou.
 * Consumidor previsto, ainda não em código: {@code notificacao} avisa a contraparte de {@code proponenteId} (US56).
 * Campos soltos pelo mesmo motivo de {@link ReagendamentoPropostoEvent} (ADR-0001).
 */
public record ReagendamentoCanceladoEvent(
        UUID reagendamentoId,
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        UUID proponenteId,
        Instant dataAnterior,
        Instant dataProposta,
        Instant ocorridoEm) {
}
