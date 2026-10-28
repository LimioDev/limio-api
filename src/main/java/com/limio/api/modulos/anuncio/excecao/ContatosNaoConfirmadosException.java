package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Quem publica precisa ter e-mail e telefone confirmados (UC "Criar anúncio de serviço"). Conta
 * encerrada também cai aqui.
 */
public class ContatosNaoConfirmadosException extends NegocioException {

    public ContatosNaoConfirmadosException() {
        super(CodigoErro.CONTATOS_NAO_CONFIRMADOS, HttpStatus.FORBIDDEN,
                CodigoErro.CONTATOS_NAO_CONFIRMADOS.mensagemPadrao());
    }
}
