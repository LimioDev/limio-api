package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Especialidade que não existe ou está inativa — recusa a gravação inteira, nada é salvo. */
public class CategoriaInvalidaException extends NegocioException {

    public CategoriaInvalidaException() {
        super(CodigoErro.CATEGORIA_INVALIDA, HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoErro.CATEGORIA_INVALIDA.mensagemPadrao());
    }
}
