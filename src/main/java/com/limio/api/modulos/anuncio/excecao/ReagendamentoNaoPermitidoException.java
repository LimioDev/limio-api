package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class ReagendamentoNaoPermitidoException extends NegocioException {

    public ReagendamentoNaoPermitidoException() {
        super(CodigoErro.REAGENDAMENTO_NAO_PERMITIDO, HttpStatus.CONFLICT,
                CodigoErro.REAGENDAMENTO_NAO_PERMITIDO.mensagemPadrao());
    }
}
