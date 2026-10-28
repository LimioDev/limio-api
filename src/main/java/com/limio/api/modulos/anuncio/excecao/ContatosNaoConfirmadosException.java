package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Telefone e/ou e-mail do Prestador ainda não confirmados (pré-condição do UC, ETI-75). */
public class ContatosNaoConfirmadosException extends NegocioException {

    public ContatosNaoConfirmadosException() {
        super(CodigoErro.CONTATOS_NAO_CONFIRMADOS, HttpStatus.FORBIDDEN,
                CodigoErro.CONTATOS_NAO_CONFIRMADOS.mensagemPadrao());
    }
}
