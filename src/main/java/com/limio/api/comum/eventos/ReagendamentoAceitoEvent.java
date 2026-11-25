package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

public record ReagendamentoAceitoEvent(
        UUID reagendamentoId,
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        UUID proponenteId,
        Instant dataAnterior,
        Instant dataNova,
        Instant ocorridoEm) {
}
