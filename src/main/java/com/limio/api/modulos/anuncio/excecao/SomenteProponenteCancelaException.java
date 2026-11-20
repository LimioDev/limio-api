package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class SomenteProponenteCancelaException extends NegocioException {

    public SomenteProponenteCancelaException() {
        super(CodigoErro.ACESSO_NEGADO, HttpStatus.FORBIDDEN, "Só quem propôs o reagendamento pode cancelá-lo.");
    }
}
