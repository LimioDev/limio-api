package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class LimiteReagendamentosException extends NegocioException {

    public LimiteReagendamentosException() {
        super(CodigoErro.LIMITE_REAGENDAMENTOS, HttpStatus.CONFLICT,
                CodigoErro.LIMITE_REAGENDAMENTOS.mensagemPadrao());
    }
}
