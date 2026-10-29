package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Candidatura inexistente ou de outro Prestador — mesma resposta pros dois, pra não revelar ids alheios (ETI-77). */
public class CandidaturaNaoEncontradaException extends NegocioException {

    public CandidaturaNaoEncontradaException() {
        super(CodigoErro.CANDIDATURA_NAO_ENCONTRADA, HttpStatus.NOT_FOUND, CodigoErro.CANDIDATURA_NAO_ENCONTRADA.mensagemPadrao());
    }
}
