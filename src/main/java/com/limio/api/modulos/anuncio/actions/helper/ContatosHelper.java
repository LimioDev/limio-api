package com.limio.api.modulos.anuncio.actions.helper;

import com.limio.api.comum.portas.DadosUsuario;

public final class ContatosHelper {

    private ContatosHelper() {
    }

    public static boolean confirmados(DadosUsuario dados) {
        return dados.emailConfirmadoEm() != null && dados.telefoneConfirmadoEm() != null;
    }
}
