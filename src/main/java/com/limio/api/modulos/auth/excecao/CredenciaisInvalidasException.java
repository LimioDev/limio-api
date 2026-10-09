package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Anti-enumeração: e-mail inexistente, senha errada e conta encerrada caem
 * todos aqui, com a mesma resposta — não revela se o e-mail tem conta.
 */
public class CredenciaisInvalidasException extends NegocioException {

    public CredenciaisInvalidasException() {
        super(CodigoErro.CREDENCIAIS_INVALIDAS, HttpStatus.UNAUTHORIZED,
                CodigoErro.CREDENCIAIS_INVALIDAS.mensagemPadrao());
    }
}
