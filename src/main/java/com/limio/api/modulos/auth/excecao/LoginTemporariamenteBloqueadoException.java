package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Rate limit do login estourado pro e-mail (ver {@code LimiteTentativasLogin}). */
public class LoginTemporariamenteBloqueadoException extends NegocioException {

    public LoginTemporariamenteBloqueadoException() {
        super(CodigoErro.LOGIN_TEMPORARIAMENTE_BLOQUEADO, HttpStatus.TOO_MANY_REQUESTS,
                CodigoErro.LOGIN_TEMPORARIAMENTE_BLOQUEADO.mensagemPadrao());
    }
}
