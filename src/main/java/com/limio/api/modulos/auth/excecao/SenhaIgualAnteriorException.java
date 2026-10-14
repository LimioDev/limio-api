package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Redefinir ou alterar senha com um valor igual ao hash atual. */
public class SenhaIgualAnteriorException extends NegocioException {

    public SenhaIgualAnteriorException() {
        super(CodigoErro.SENHA_IGUAL_ANTERIOR, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.SENHA_IGUAL_ANTERIOR.mensagemPadrao());
    }
}
