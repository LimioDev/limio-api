package com.limio.api.modulos.chat.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class ChatSomenteLeituraException extends NegocioException {

    public ChatSomenteLeituraException() {
        super(CodigoErro.CHAT_SOMENTE_LEITURA, HttpStatus.CONFLICT, CodigoErro.CHAT_SOMENTE_LEITURA.mensagemPadrao());
    }
}
