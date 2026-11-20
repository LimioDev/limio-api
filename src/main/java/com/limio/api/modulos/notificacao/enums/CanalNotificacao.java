package com.limio.api.modulos.notificacao.enums;

/**
 * Canal por onde um aviso sai (ETI-112), os mesmos da {@code PreferenciaNotificacao}. SMS saiu de escopo no ETI-134.
 */
public enum CanalNotificacao {
    PUSH,
    EMAIL,
    WHATSAPP
}
