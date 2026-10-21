package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** A geocodificação não achou município com esse nome na UF informada (ex.: erro de digitação). */
public class MunicipioNaoEncontradoException extends NegocioException {

    public MunicipioNaoEncontradoException() {
        super(CodigoErro.MUNICIPIO_NAO_ENCONTRADO, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.MUNICIPIO_NAO_ENCONTRADO.mensagemPadrao());
    }
}
