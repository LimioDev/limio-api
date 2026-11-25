package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

public record ReagendamentoRecusadoEvent(
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
