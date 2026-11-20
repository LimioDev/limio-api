package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.anuncio.enums.StatusReagendamento;

public record ReagendamentoResponse(
        UUID id,
        UUID anuncioId,
        StatusReagendamento status,
        Instant dataAnterior,
        Instant dataProposta,
        String justificativa,
        UUID proponenteId,
        Instant criadoEm,
        Instant expiraEm) {
}
