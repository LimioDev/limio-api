package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * E-mail e/ou telefone ainda não confirmados: pré-condição de publicar anúncio (ETI-59) e de se candidatar
 * (ETI-75). Conta encerrada também cai aqui.
 */
public class ContatosNaoConfirmadosException extends NegocioException {

    public ContatosNaoConfirmadosException() {
        super(CodigoErro.CONTATOS_NAO_CONFIRMADOS, HttpStatus.FORBIDDEN,
                CodigoErro.CONTATOS_NAO_CONFIRMADOS.mensagemPadrao());
    }
}
