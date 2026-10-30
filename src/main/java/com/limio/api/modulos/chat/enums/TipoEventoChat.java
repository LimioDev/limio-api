package com.limio.api.modulos.chat.enums;

/** O que chega pelo canal em tempo real ({@code /user/queue/chat}). */
public enum TipoEventoChat {
    /** Mensagem nova, para o destinatário. */
    MENSAGEM_NOVA,
    /** Mensagens que chegaram ao aparelho da outra parte, para o remetente. */
    ENTREGUE,
    /** Mensagens lidas pela outra parte, para o remetente. */
    LIDA
}
