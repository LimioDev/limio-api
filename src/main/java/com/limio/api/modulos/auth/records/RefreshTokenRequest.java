package com.limio.api.modulos.auth.records;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code /auth/refresh} e {@code /auth/logout}: o refresh token do aparelho. */
public record RefreshTokenRequest(
        @NotBlank(message = "{sessao.refreshToken.obrigatorio}")
        String refreshToken) {
}
