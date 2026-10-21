package com.limio.api.modulos.auth.records;

import jakarta.validation.constraints.AssertTrue;

/** Corpo de {@code POST /auth/encerrar-conta}. Sem a confirmação marcada, nem chega no usecase (400 genérico). */
public record EncerrarContaRequest(
        @AssertTrue(message = "{encerrarConta.confirmacao.obrigatoria}")
        boolean confirmacao) {
}
