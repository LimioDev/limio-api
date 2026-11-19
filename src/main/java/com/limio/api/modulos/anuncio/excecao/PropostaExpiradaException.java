package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class PropostaExpiradaException extends NegocioException {

    public PropostaExpiradaException() {
        super(CodigoErro.PROPOSTA_EXPIRADA, HttpStatus.CONFLICT,
                CodigoErro.PROPOSTA_EXPIRADA.mensagemPadrao());
    }
}
