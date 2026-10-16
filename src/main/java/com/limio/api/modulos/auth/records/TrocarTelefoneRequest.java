package com.limio.api.modulos.auth.records;

import com.limio.api.modulos.auth.actions.helper.TelefoneHelper;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TrocarTelefoneRequest(
        @NotBlank(message = "{trocarTelefone.novoTelefone.obrigatorio}")
        @Pattern(regexp = "[1-9][1-9]9\\d{8}", message = "{trocarTelefone.novoTelefone.formato}")
        String novoTelefone,

        @NotBlank(message = "{alterarSenha.senhaAtual.obrigatoria}")
        String senhaAtual) {

    public TrocarTelefoneRequest {
        novoTelefone = TelefoneHelper.normalizar(novoTelefone)
                .orElse(novoTelefone == null ? null : novoTelefone.strip());
    }
}
