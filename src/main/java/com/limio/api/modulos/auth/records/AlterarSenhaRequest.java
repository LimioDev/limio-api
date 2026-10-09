package com.limio.api.modulos.auth.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Corpo de {@code PATCH /auth/senha} (logado). {@code refreshToken} identifica
 * a sessão deste aparelho — é ela que recebe os tokens novos, as demais são
 * encerradas (mesmo contrato de {@link RefreshTokenRequest}).
 */
public record AlterarSenhaRequest(
        @NotBlank(message = "{alterarSenha.senhaAtual.obrigatoria}")
        String senhaAtual,

        @NotBlank(message = "{alterarSenha.novaSenha.obrigatoria}")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$", message = "{alterarSenha.novaSenha.formato}")
        String novaSenha,

        @NotBlank(message = "{sessao.refreshToken.obrigatorio}")
        String refreshToken) {
}
