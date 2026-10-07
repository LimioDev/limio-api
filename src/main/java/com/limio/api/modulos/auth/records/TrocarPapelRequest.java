package com.limio.api.modulos.auth.records;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

import jakarta.validation.constraints.NotNull;

/** Só EMPREGADOR ou PRESTADOR — a regra fica no usecase (ADMIN é recusado lá com PAPEL_INVALIDO). */
public record TrocarPapelRequest(
        @NotNull(message = "{papel.obrigatorio}")
        PapelUsuario papel) {
}
