package com.limio.api.modulos.notificacao.enums;

/** Resultado do envio de um aviso por um canal (ETI-112). */
public enum StatusEnvio {
    /** O provedor aceitou o envio. */
    ENVIADO,
    /** O provedor recusou ou não respondeu; o aviso continua na lista do app. */
    FALHOU
}
