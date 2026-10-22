package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Storage fora do ar, recusando a operação ou não configurado (sem STORAGE_BUCKET). */
public class ArmazenamentoIndisponivelException extends NegocioException {

    public ArmazenamentoIndisponivelException() {
        super(CodigoErro.ARMAZENAMENTO_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE,
                CodigoErro.ARMAZENAMENTO_INDISPONIVEL.mensagemPadrao());
    }
}
