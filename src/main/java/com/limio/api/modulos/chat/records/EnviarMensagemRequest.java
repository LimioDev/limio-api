package com.limio.api.modulos.chat.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /chats/{chatId}/mensagens}. O limite de 2000 caracteres é provisório, até a decisão do PO; o
 * banco guarda {@code TEXT} e não precisa mudar.
 */
public record EnviarMensagemRequest(
        @NotBlank(message = "{mensagem.texto.obrigatorio}")
        @Size(max = EnviarMensagemRequest.TAMANHO_MAXIMO, message = "{mensagem.texto.tamanho}")
        String texto) {

    public static final int TAMANHO_MAXIMO = 2000;

    public EnviarMensagemRequest {
        texto = texto == null ? null : texto.strip();
    }
}
