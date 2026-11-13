package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class FotoObrigatoriaException extends NegocioException {

    public FotoObrigatoriaException() {
        super(CodigoErro.FOTO_OBRIGATORIA, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.FOTO_OBRIGATORIA.mensagemPadrao());
    }
}
