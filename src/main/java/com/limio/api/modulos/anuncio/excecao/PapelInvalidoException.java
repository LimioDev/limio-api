package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Quem chama está logado com papel ativo diferente de {@code PRESTADOR} (ETI-75). */
public class PapelInvalidoException extends NegocioException {

    public PapelInvalidoException() {
        super(CodigoErro.PAPEL_INVALIDO, HttpStatus.FORBIDDEN, CodigoErro.PAPEL_INVALIDO.mensagemPadrao());
    }
}
