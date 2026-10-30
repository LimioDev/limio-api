package com.limio.api.modulos.chat.records;

import java.time.Instant;
import java.util.UUID;

/** {@code texto} é o texto exibido (com o mascaramento da US50, quando entrar), nunca o original. */
public record MensagemResponse(
        UUID id,
        UUID chatId,
        UUID remetenteId,
        String texto,
        Instant dataEnvio,
        Instant entregueEm,
        Instant lidaEm) {
}
