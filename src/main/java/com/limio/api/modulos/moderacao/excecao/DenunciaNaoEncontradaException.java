package com.limio.api.modulos.moderacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Denúncia inexistente na análise ou na decisão do administrador (ETI-116). */
public class DenunciaNaoEncontradaException extends NegocioException {

    public DenunciaNaoEncontradaException() {
        super(CodigoErro.DENUNCIA_NAO_ENCONTRADA, HttpStatus.NOT_FOUND, CodigoErro.DENUNCIA_NAO_ENCONTRADA.mensagemPadrao());
    }
}
