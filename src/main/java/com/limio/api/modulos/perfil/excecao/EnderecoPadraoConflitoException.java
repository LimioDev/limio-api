package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class EnderecoPadraoConflitoException extends NegocioException {

    public EnderecoPadraoConflitoException() {
        super(CodigoErro.ENDERECO_PADRAO_CONFLITO, HttpStatus.CONFLICT,
                CodigoErro.ENDERECO_PADRAO_CONFLITO.mensagemPadrao());
    }
}
