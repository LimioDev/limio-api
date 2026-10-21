package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class ContatoNaoPermitidoException extends NegocioException {

    public ContatoNaoPermitidoException() {
        super(CodigoErro.CONTATO_NAO_PERMITIDO, HttpStatus.CONFLICT,
                CodigoErro.CONTATO_NAO_PERMITIDO.mensagemPadrao());
    }
}
