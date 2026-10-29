package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Anúncio inexistente ou de outro usuário: as duas situações respondem igual, pra não revelar que o
 * anúncio existe.
 */
public class AnuncioNaoEncontradoException extends NegocioException {

    public AnuncioNaoEncontradoException() {
        super(CodigoErro.ANUNCIO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND,
                CodigoErro.ANUNCIO_NAO_ENCONTRADO.mensagemPadrao());
    }
}
