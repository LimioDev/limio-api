package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma das partes propôs nova data pro serviço combinado (ETI-99). Consumidor previsto, ainda não em código: {@code
 * notificacao} avisa a contraparte de {@code proponenteId} (US56). {@code anuncioTitulo} vai solto pelo mesmo
 * motivo de {@link CandidaturaCriadaEvent}: quem escuta não pode importar {@code modulos.anuncio} (ADR-0001).
 * {@code dataAnterior} é a data vigente enquanto a proposta não for aceita.
 */
public record ReagendamentoPropostoEvent(
        UUID reagendamentoId,
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        UUID proponenteId,
        Instant dataAnterior,
        Instant dataProposta,
        Instant expiraEm,
        Instant ocorridoEm) {
}
