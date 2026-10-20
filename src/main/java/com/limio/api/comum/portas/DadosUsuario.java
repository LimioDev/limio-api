package com.limio.api.comum.portas;

import java.time.Instant;

public record DadosUsuario(
        String nomeCompleto,
        String email,
        String telefone,
        Instant emailConfirmadoEm,
        Instant telefoneConfirmadoEm) {
}
