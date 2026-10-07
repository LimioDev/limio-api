package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Conta com {@code StatusConta.BLOQUEADA} (suspensa por moderação). No login
 * só é lançada depois da senha conferir, pra não revelar o status a quem não
 * tem a senha.
 */
public class ContaSuspensaException extends NegocioException {

    public ContaSuspensaException() {
        super(CodigoErro.CONTA_SUSPENSA, HttpStatus.FORBIDDEN,
                CodigoErro.CONTA_SUSPENSA.mensagemPadrao());
    }
}
