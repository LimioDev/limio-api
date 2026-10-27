package com.limio.api.modulos.auth.records;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /auth/reenviar-confirmacao-email}. Resposta é sempre genérica — ver o usecase. */
public record ReenviarConfirmacaoEmailRequest(
        @NotBlank(message = "{reenvioConfirmacao.email.obrigatorio}")
        @Email(message = "{reenvioConfirmacao.email.formato}")
        @Size(max = 255, message = "{reenvioConfirmacao.email.tamanho}")
        String email) {

    public ReenviarConfirmacaoEmailRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
