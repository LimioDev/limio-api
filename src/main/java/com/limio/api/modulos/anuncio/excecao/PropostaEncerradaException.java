package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class PropostaEncerradaException extends NegocioException {

    public PropostaEncerradaException() {
        super(CodigoErro.PROPOSTA_ENCERRADA, HttpStatus.CONFLICT,
                CodigoErro.PROPOSTA_ENCERRADA.mensagemPadrao());
    }
}
