package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Refresh token desconhecido, já rotacionado, de sessão revogada/expirada ou de conta encerrada. */
public class SessaoInvalidaException extends NegocioException {

    public SessaoInvalidaException() {
        super(CodigoErro.SESSAO_INVALIDA, HttpStatus.UNAUTHORIZED,
                CodigoErro.SESSAO_INVALIDA.mensagemPadrao());
    }
}
