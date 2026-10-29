package com.limio.api.comum.portas;

import java.time.Instant;

public record DadosUsuario(
        String nomeCompleto,
        String email,
        String telefone,
        Instant emailConfirmadoEm,
        Instant telefoneConfirmadoEm) {

    /** E-mail e telefone confirmados: pré-condição de publicar anúncio e de se candidatar. */
    public boolean contatosConfirmados() {
        return emailConfirmadoEm != null && telefoneConfirmadoEm != null;
    }
}
