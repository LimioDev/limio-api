package com.limio.api.modulos.notificacao.records;

import jakarta.validation.constraints.NotNull;

/**
 * Estado completo dos canais (PUT): os quatro são obrigatórios. {@link Boolean}
 * em vez de {@code boolean} pra campo ausente chegar como {@code null} e ser
 * recusado pelo {@code @NotNull}, em vez de virar {@code false} silenciosamente.
 * Texto das mensagens em {@code ValidationMessages.properties}.
 */
public record PreferenciaNotificacaoRequest(
        @NotNull(message = "{preferenciaNotificacao.push.obrigatorio}")
        Boolean push,

        @NotNull(message = "{preferenciaNotificacao.email.obrigatorio}")
        Boolean email,

        @NotNull(message = "{preferenciaNotificacao.sms.obrigatorio}")
        Boolean sms,

        @NotNull(message = "{preferenciaNotificacao.whatsapp.obrigatorio}")
        Boolean whatsapp) {
}
