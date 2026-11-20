package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class ProponenteNaoRespondeException extends NegocioException {

    public ProponenteNaoRespondeException() {
        super(CodigoErro.ACESSO_NEGADO, HttpStatus.FORBIDDEN,
                "Quem propôs o reagendamento não pode respondê-lo. Para desistir, cancele a proposta.");
    }
}
