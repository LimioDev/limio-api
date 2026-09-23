package com.limio.api.comum.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.enums.CodigoErro;

/** Exceção técnica genérica de {@link com.limio.api.comum.base.BaseService} — busca por id sem resultado. */
public class EntidadeNaoEncontradaException extends NegocioException {

    public EntidadeNaoEncontradaException() {
        super(CodigoErro.ENTIDADE_NAO_ENCONTRADA, HttpStatus.NOT_FOUND,
                CodigoErro.ENTIDADE_NAO_ENCONTRADA.mensagemPadrao());
    }
}
