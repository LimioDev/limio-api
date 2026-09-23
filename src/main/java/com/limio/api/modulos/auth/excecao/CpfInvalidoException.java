package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class CpfInvalidoException extends NegocioException {

    public CpfInvalidoException() {
        super(CodigoErro.CADASTRO_CPF_INVALIDO, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.CADASTRO_CPF_INVALIDO.mensagemPadrao());
    }
}
