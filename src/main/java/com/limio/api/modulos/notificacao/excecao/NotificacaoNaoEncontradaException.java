package com.limio.api.modulos.notificacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Notificação inexistente ou de outro usuário — mesma resposta pros dois, pra não revelar ids alheios (ETI-110). */
public class NotificacaoNaoEncontradaException extends NegocioException {

    public NotificacaoNaoEncontradaException() {
        super(CodigoErro.NOTIFICACAO_NAO_ENCONTRADA, HttpStatus.NOT_FOUND,
                CodigoErro.NOTIFICACAO_NAO_ENCONTRADA.mensagemPadrao());
    }
}
