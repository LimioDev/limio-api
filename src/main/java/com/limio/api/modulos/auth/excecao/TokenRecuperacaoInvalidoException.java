package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Token de recuperação de senha expirado, já usado ou inexistente. */
public class TokenRecuperacaoInvalidoException extends NegocioException {

    public TokenRecuperacaoInvalidoException() {
        super(CodigoErro.TOKEN_RECUPERACAO_INVALIDO, HttpStatus.UNAUTHORIZED,
                CodigoErro.TOKEN_RECUPERACAO_INVALIDO.mensagemPadrao());
    }
}
