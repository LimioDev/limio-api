package com.limio.api.modulos.auth.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Corpo de {@code POST /auth/redefinir-senha} ("esqueci minha senha", token do e-mail). */
public record RedefinirSenhaRequest(
        @NotBlank(message = "{redefinirSenha.token.obrigatorio}")
        String token,

        @NotBlank(message = "{redefinirSenha.novaSenha.obrigatoria}")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "{redefinirSenha.novaSenha.formato}")
        String novaSenha) {
}
