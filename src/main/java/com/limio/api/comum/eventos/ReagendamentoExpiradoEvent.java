package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O prazo de resposta de uma proposta de reagendamento terminou sem manifestação da contraparte (ETI-99): a proposta
 * foi encerrada sem efeito e a data original continua vigente. Consumidor previsto, ainda não em código: {@code
 * notificacao} avisa as duas partes (US56). Campos soltos pelo mesmo motivo de {@link ReagendamentoPropostoEvent}
 * (ADR-0001).
 */
public record ReagendamentoExpiradoEvent(
        UUID reagendamentoId,
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        UUID proponenteId,
        Instant dataVigente,
        Instant dataProposta,
        Instant ocorridoEm) {
}
