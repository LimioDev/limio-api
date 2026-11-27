package com.limio.api.modulos.financeiro.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Assinar GOLD sem {@code gatewayToken} e sem {@code MeioPagamento} salvo (ETI-81): não há cartão pra
 * cobrar. Lançada antes de reservar a assinatura, então não deixa pendência.
 */
public class CartaoNaoCadastradoException extends NegocioException {

    public CartaoNaoCadastradoException() {
        super(CodigoErro.CARTAO_NAO_CADASTRADO, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.CARTAO_NAO_CADASTRADO.mensagemPadrao());
    }
}
