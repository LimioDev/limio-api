package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class FotoNaoEncontradaException extends NegocioException {

    public FotoNaoEncontradaException() {
        super(CodigoErro.ENTIDADE_NAO_ENCONTRADA, HttpStatus.NOT_FOUND, "Foto não encontrada.");
    }
}
