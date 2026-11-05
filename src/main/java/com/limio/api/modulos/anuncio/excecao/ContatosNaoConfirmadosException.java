package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Quem publica (UC "Criar anúncio de serviço") ou vê o mural (ETI-73) precisa ter e-mail e telefone
 * confirmados. Conta encerrada também cai aqui.
 * E-mail e/ou telefone ainda não confirmados: pré-condição de publicar anúncio (ETI-59) e de se candidatar
 * (ETI-75). Conta encerrada também cai aqui.
 */
public class ContatosNaoConfirmadosException extends NegocioException {

    public ContatosNaoConfirmadosException() {
        this(CodigoErro.CONTATOS_NAO_CONFIRMADOS.mensagemPadrao());
    }

    /** Mesmo código, mensagem do cenário (a padrão fala em publicar anúncio). */
    public ContatosNaoConfirmadosException(String mensagem) {
        super(CodigoErro.CONTATOS_NAO_CONFIRMADOS, HttpStatus.FORBIDDEN, mensagem);
    }
}
