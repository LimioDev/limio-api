package com.limio.api.modulos.notificacao.records;

/**
 * Estado dos canais do usuário logado — o salvo ou, se ele nunca salvou, os
 * padrões de conta nova.
 */
public record PreferenciaNotificacaoResponse(
        Boolean push,
        Boolean email,
        Boolean sms,
        Boolean whatsapp) {
}
