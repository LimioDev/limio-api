package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Categoria inexistente ou inativa na criação do anúncio. */
public class CategoriaIndisponivelException extends NegocioException {

    public CategoriaIndisponivelException() {
        super(CodigoErro.CATEGORIA_INVALIDA, HttpStatus.UNPROCESSABLE_ENTITY,
                "A categoria escolhida não está disponível.");
    }
}
