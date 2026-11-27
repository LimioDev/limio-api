package com.limio.api.modulos.financeiro.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** O aviso do gateway chegou sem o token de autenticação esperado (ETI-85): recusado sem processar. */
public class WebhookInvalidoException extends NegocioException {

    public WebhookInvalidoException() {
        super(CodigoErro.WEBHOOK_INVALIDO, HttpStatus.UNAUTHORIZED, CodigoErro.WEBHOOK_INVALIDO.mensagemPadrao());
    }
}
