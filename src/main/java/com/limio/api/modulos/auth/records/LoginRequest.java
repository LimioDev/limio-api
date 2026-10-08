package com.limio.api.modulos.auth.records;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code dispositivo} é opcional: identifica o aparelho na {@code Sessao}
 * (ex.: "iPhone da Maria"). Sem ele, o controller usa o User-Agent.
 *
 * {@code email} limitado a 255 (tamanho da coluna): {@code @Email} sozinho
 * aceita até 320 caracteres, e o excesso estourava no banco como 500.
 */
public record LoginRequest(
        @NotBlank(message = "{login.email.obrigatorio}")
        @Email(message = "{login.email.formato}")
        @Size(max = 255, message = "{login.email.tamanho}")
        String email,

        @NotBlank(message = "{login.senha.obrigatoria}")
        String senha,

        @Size(max = 255, message = "{login.dispositivo.tamanho}")
        String dispositivo) {

    public LoginRequest {
        // Locale.ROOT: lowercase não pode depender do idioma do servidor (ex.: "I" vira "ı" em turco)
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        dispositivo = dispositivo == null || dispositivo.isBlank() ? null : dispositivo.trim();
    }
}
