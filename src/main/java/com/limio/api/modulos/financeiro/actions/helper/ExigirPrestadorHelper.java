package com.limio.api.modulos.financeiro.actions.helper;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.financeiro.excecao.SomentePrestadorAssinaGoldException;

/** Só o Prestador usa o GOLD (ETI-81) — repetido por todo usecase do módulo que precisa dessa checagem. */
public final class ExigirPrestadorHelper {

    private ExigirPrestadorHelper() {
    }

    public static void exigirPrestador(PapelUsuario papelAtivo) {
        switch (papelAtivo) {
            case PRESTADOR -> {
            }
            case EMPREGADOR -> throw new SomentePrestadorAssinaGoldException();
        }
    }
}
