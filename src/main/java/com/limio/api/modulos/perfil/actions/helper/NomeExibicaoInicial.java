package com.limio.api.modulos.perfil.actions.helper;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;

public final class NomeExibicaoInicial {

    private NomeExibicaoInicial() {
    }

    public static String de(String nomeCompleto) {
        String nome = nomeCompleto.strip();
        return nome.length() <= PerfilEmpregador.NOME_EXIBICAO_MAX
                ? nome
                : nome.substring(0, PerfilEmpregador.NOME_EXIBICAO_MAX).stripTrailing();
    }
}
