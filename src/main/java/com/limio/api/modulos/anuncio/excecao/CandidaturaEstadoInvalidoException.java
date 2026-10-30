package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Candidatura já recusada, concluída ou encerrada — não há mais do que desistir (ETI-77). */
public class CandidaturaEstadoInvalidoException extends NegocioException {

    public CandidaturaEstadoInvalidoException() {
        super(CodigoErro.CANDIDATURA_ESTADO_INVALIDO, HttpStatus.CONFLICT,
                CodigoErro.CANDIDATURA_ESTADO_INVALIDO.mensagemPadrao());
    }
}
