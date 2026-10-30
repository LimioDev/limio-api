package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Prestador tentou se candidatar a um anúncio do qual ele mesmo é o contratante (ETI-75). */
public class AnuncioProprioException extends NegocioException {

    public AnuncioProprioException() {
        super(CodigoErro.ANUNCIO_PROPRIO, HttpStatus.FORBIDDEN, CodigoErro.ANUNCIO_PROPRIO.mensagemPadrao());
    }
}
