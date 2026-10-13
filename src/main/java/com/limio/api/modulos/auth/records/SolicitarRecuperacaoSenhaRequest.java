package com.limio.api.modulos.auth.records;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /auth/recuperar-senha}. Resposta é sempre genérica — ver o usecase. */
public record SolicitarRecuperacaoSenhaRequest(
        @NotBlank(message = "{recuperacaoSenha.email.obrigatorio}")
        @Email(message = "{recuperacaoSenha.email.formato}")
        @Size(max = 255, message = "{recuperacaoSenha.email.tamanho}")
        String email) {

    public SolicitarRecuperacaoSenhaRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
