package com.limio.api.modulos.auth.records;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

import jakarta.validation.constraints.NotNull;

/** EMPREGADOR ou PRESTADOR; qualquer outro valor não desserializa e responde 400 {@code VALIDACAO_FALHOU}. */
public record TrocarPapelRequest(
        @NotNull(message = "{papel.obrigatorio}")
        PapelUsuario papel) {
}
