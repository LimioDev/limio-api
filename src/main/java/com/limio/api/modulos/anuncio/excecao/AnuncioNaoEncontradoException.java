package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Anúncio inexistente ou de outro contratante (ETI-63) — mesma resposta pros dois casos: quem não é
 * dono não descobre se o id existe.
 */
public class AnuncioNaoEncontradoException extends NegocioException {

    public AnuncioNaoEncontradoException() {
        super(CodigoErro.ANUNCIO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND, CodigoErro.ANUNCIO_NAO_ENCONTRADO.mensagemPadrao());
    }
}
