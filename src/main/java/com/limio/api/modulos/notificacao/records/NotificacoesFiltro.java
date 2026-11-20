package com.limio.api.modulos.notificacao.records;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Query string de {@code GET /notificacoes} (ETI-110). {@code pagina} começa em 0 e é opcional.
 * Texto da mensagem em {@code ValidationMessages.properties}.
 */
public record NotificacoesFiltro(@PositiveOrZero(message = "{notificacoes.pagina.naoNegativa}") Integer pagina) {

    public NotificacoesFiltro {
        if (pagina == null) {
            pagina = 0;
        }
    }
}
