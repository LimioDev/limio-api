package com.limio.api.modulos.auth.records;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code POST /auth/confirmar-email} — token do link do e-mail (página {@code /confirmar-email}). */
public record ConfirmarEmailRequest(
        @NotBlank(message = "{confirmarEmail.token.obrigatorio}")
        String token) {
}
