package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Usuário inexistente (ETI-105) — diferente de conta encerrada, que devolve 200 com o perfil anonimizado. */
public class PerfilPublicoNaoEncontradoException extends NegocioException {

    public PerfilPublicoNaoEncontradoException() {
        super(CodigoErro.PERFIL_NAO_ENCONTRADO, HttpStatus.NOT_FOUND, CodigoErro.PERFIL_NAO_ENCONTRADO.mensagemPadrao());
    }
}
