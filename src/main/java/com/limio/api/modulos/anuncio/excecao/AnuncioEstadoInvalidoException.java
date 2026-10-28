package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Anúncio fora de {@code PUBLICADO}, ou já com candidatura escolhida — não aceita a ação pedida
 * (candidatar-se, ETI-75; cancelar, ETI-63).
 */
public class AnuncioEstadoInvalidoException extends NegocioException {

    public AnuncioEstadoInvalidoException() {
        super(CodigoErro.ANUNCIO_ESTADO_INVALIDO, HttpStatus.CONFLICT,
                CodigoErro.ANUNCIO_ESTADO_INVALIDO.mensagemPadrao());
    }
}
