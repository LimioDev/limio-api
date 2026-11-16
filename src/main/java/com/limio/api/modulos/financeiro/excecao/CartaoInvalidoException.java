package com.limio.api.modulos.financeiro.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Asaas recusou tokenizar o cartão — número, validade ou CVV inválidos (ETI-81, integração Asaas). */
public class CartaoInvalidoException extends NegocioException {

    public CartaoInvalidoException() {
        super(CodigoErro.CARTAO_INVALIDO, HttpStatus.BAD_REQUEST, CodigoErro.CARTAO_INVALIDO.mensagemPadrao());
    }
}
