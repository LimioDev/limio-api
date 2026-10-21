package com.limio.api.modulos.auth.records;

import java.time.Instant;

public record ContatoResponse(
        String email,
        String telefone,
        Instant emailConfirmadoEm,
        Instant telefoneConfirmadoEm) {
}
