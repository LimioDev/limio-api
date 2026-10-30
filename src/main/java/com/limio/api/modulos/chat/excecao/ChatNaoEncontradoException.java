package com.limio.api.modulos.chat.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Chat que não existe ou de que quem pede não é parte: as duas respostas são iguais de propósito. */
public class ChatNaoEncontradoException extends NegocioException {

    public ChatNaoEncontradoException() {
        super(CodigoErro.CHAT_NAO_ENCONTRADO, HttpStatus.NOT_FOUND, CodigoErro.CHAT_NAO_ENCONTRADO.mensagemPadrao());
    }
}
