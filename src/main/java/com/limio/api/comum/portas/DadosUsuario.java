package com.limio.api.comum.portas;

import java.time.Instant;

public record DadosUsuario(String nomeCompleto, String email, String telefone, Instant emailConfirmadoEm) {

    /**
     * E-mail confirmado: pré-condição de publicar anúncio e de se candidatar (ETI-134). A confirmação
     * de telefone por SMS (UC04) nunca chegou a existir — telefone segue obrigatório/editável, mas sem
     * etapa de confirmação nem efeito em nenhuma regra.
     */
    public boolean emailConfirmado() {
        return emailConfirmadoEm != null;
    }
}
