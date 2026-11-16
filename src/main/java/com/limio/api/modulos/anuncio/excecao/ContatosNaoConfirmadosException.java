package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Quem publica (UC "Criar anúncio de serviço"), vê o mural (ETI-73) ou se candidata (ETI-75) precisa ter
 * e-mail confirmado (ETI-134 — telefone não exige confirmação, UC04/SMS nunca existiu). Conta encerrada
 * também cai aqui.
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
