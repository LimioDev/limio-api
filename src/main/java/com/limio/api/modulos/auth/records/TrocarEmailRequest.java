package com.limio.api.modulos.auth.records;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocarEmailRequest(
        @NotBlank(message = "{trocarEmail.novoEmail.obrigatorio}")
        @Email(message = "{trocarEmail.novoEmail.formato}")
        @Size(max = 255, message = "{trocarEmail.novoEmail.tamanho}")
        String novoEmail,

        @NotBlank(message = "{alterarSenha.senhaAtual.obrigatoria}")
        String senhaAtual) {

    public TrocarEmailRequest {
        novoEmail = novoEmail == null ? null : novoEmail.trim().toLowerCase(Locale.ROOT);
    }
}
