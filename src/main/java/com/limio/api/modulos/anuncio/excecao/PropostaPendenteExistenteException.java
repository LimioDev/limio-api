package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class PropostaPendenteExistenteException extends NegocioException {

    public PropostaPendenteExistenteException() {
        super(CodigoErro.PROPOSTA_PENDENTE_EXISTENTE, HttpStatus.CONFLICT,
                CodigoErro.PROPOSTA_PENDENTE_EXISTENTE.mensagemPadrao());
    }
}
