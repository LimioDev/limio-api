package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class PerfilEmpregadorNaoEncontradoException extends NegocioException {

    public PerfilEmpregadorNaoEncontradoException() {
        super(CodigoErro.PERFIL_EMPREGADOR_NAO_ENCONTRADO, HttpStatus.NOT_FOUND,
                CodigoErro.PERFIL_EMPREGADOR_NAO_ENCONTRADO.mensagemPadrao());
    }
}
