package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** A geocodificação respondeu, mas não achou o endereço informado (ex.: erro de digitação na rua ou no número). */
public class EnderecoNaoLocalizadoException extends NegocioException {

    public EnderecoNaoLocalizadoException() {
        super(CodigoErro.ENDERECO_NAO_LOCALIZADO, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.ENDERECO_NAO_LOCALIZADO.mensagemPadrao());
    }
}
