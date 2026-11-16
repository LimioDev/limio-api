package com.limio.api.modulos.financeiro.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** {@code codigoPlano} do request de assinar não corresponde a nenhum plano ativo (ETI-81). */
public class PlanoNaoEncontradoException extends NegocioException {

    public PlanoNaoEncontradoException() {
        super(CodigoErro.PLANO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND, CodigoErro.PLANO_NAO_ENCONTRADO.mensagemPadrao());
    }
}
