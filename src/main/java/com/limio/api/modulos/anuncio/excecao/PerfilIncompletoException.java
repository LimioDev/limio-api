package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class PerfilIncompletoException extends NegocioException {

    public PerfilIncompletoException() {
        super(CodigoErro.PERFIL_INCOMPLETO, HttpStatus.FORBIDDEN, CodigoErro.PERFIL_INCOMPLETO.mensagemPadrao());
    }
}
