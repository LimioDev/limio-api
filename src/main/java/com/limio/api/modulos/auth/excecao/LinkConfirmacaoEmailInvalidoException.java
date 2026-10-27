package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Link de confirmação de e-mail inexistente, expirado, já usado ou de conta encerrada. Mesmo status
 * do {@link TokenRecuperacaoInvalidoException}: os dois são "o link do e-mail não vale".
 */
public class LinkConfirmacaoEmailInvalidoException extends NegocioException {

    public LinkConfirmacaoEmailInvalidoException() {
        super(CodigoErro.LINK_CONFIRMACAO_EMAIL_INVALIDO, HttpStatus.UNAUTHORIZED,
                CodigoErro.LINK_CONFIRMACAO_EMAIL_INVALIDO.mensagemPadrao());
    }
}
