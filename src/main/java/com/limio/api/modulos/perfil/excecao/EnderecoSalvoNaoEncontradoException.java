package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class EnderecoSalvoNaoEncontradoException extends NegocioException {

    public EnderecoSalvoNaoEncontradoException() {
        super(CodigoErro.ENDERECO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND,
                CodigoErro.ENDERECO_NAO_ENCONTRADO.mensagemPadrao());
    }
}
