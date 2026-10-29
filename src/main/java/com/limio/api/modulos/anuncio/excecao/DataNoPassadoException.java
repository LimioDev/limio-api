package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Data e hora pretendidas que não estão no futuro (UC "Criar anúncio de serviço"). */
public class DataNoPassadoException extends NegocioException {

    public DataNoPassadoException() {
        super(CodigoErro.DATA_NO_PASSADO, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.DATA_NO_PASSADO.mensagemPadrao());
    }
}
