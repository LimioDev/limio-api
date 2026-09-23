package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class IdadeMinimaNaoAtingidaException extends NegocioException {

    public IdadeMinimaNaoAtingidaException() {
        super(CodigoErro.CADASTRO_IDADE_MINIMA, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.CADASTRO_IDADE_MINIMA.mensagemPadrao());
    }
}
