package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class TrocaContatoLimiteExcedidaException extends NegocioException {

    public TrocaContatoLimiteExcedidaException() {
        super(CodigoErro.TROCA_CONTATO_LIMITE_EXCEDIDO, HttpStatus.TOO_MANY_REQUESTS,
                CodigoErro.TROCA_CONTATO_LIMITE_EXCEDIDO.mensagemPadrao());
    }
}
