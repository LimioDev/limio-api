package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class ReagendamentoNaoEncontradoException extends NegocioException {

    public ReagendamentoNaoEncontradoException() {
        super(CodigoErro.REAGENDAMENTO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND,
                CodigoErro.REAGENDAMENTO_NAO_ENCONTRADO.mensagemPadrao());
    }
}
