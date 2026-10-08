package com.limio.api.modulos.notificacao.records;

/**
 * Estado dos canais do usuário logado. Canal pode vir {@code null} só quando o
 * usuário nunca salvou preferência e aquele canal ainda não tem padrão de conta
 * nova definido (ver pendências em {@code docs/features/UC15-...md}).
 */
public record PreferenciaNotificacaoResponse(
        Boolean push,
        Boolean email,
        Boolean sms,
        Boolean whatsapp) {
}
