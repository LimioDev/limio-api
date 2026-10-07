package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Troca de papel pra algo que não é EMPREGADOR nem PRESTADOR (ex.: ADMIN). */
public class PapelInvalidoException extends NegocioException {

    public PapelInvalidoException() {
        super(CodigoErro.PAPEL_INVALIDO, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.PAPEL_INVALIDO.mensagemPadrao());
    }
}
