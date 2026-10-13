package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Alterar senha (logado): senha atual informada não confere com o hash gravado. */
public class SenhaAtualIncorretaException extends NegocioException {

    public SenhaAtualIncorretaException() {
        super(CodigoErro.SENHA_ATUAL_INCORRETA, HttpStatus.UNAUTHORIZED,
                CodigoErro.SENHA_ATUAL_INCORRETA.mensagemPadrao());
    }
}
