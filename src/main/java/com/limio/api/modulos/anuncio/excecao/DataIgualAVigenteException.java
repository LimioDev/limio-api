package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Proposta de reagendamento para a mesma data e hora que o serviço já tem (ETI-99). */
public class DataIgualAVigenteException extends NegocioException {

    public DataIgualAVigenteException() {
        super(CodigoErro.DATA_IGUAL_A_VIGENTE, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.DATA_IGUAL_A_VIGENTE.mensagemPadrao());
    }
}
