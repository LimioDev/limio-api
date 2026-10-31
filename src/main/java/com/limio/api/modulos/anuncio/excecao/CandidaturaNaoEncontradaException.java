package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Candidatura inexistente ou que não pertence ao anúncio informado: as duas situações respondem igual. */
public class CandidaturaNaoEncontradaException extends NegocioException {

    public CandidaturaNaoEncontradaException() {
        super(CodigoErro.CANDIDATURA_NAO_ENCONTRADA, HttpStatus.NOT_FOUND,
                CodigoErro.CANDIDATURA_NAO_ENCONTRADA.mensagemPadrao());
    }
}
