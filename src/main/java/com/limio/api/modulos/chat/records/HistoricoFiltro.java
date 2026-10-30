package com.limio.api.modulos.chat.records;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Query de {@code GET /candidaturas/{candidaturaId}/chat}. Sem {@code antesDe}, as mensagens mais recentes; com ele,
 * as anteriores a essa mensagem. {@code tamanho} fora de 1 a {@value #TAMANHO_MAXIMO} responde 400.
 */
public record HistoricoFiltro(
        UUID antesDe,
        @Min(value = 1, message = "{chat.historico.tamanho}")
        @Max(value = HistoricoFiltro.TAMANHO_MAXIMO, message = "{chat.historico.tamanho}")
        Integer tamanho) {

    public static final int TAMANHO_PADRAO = 30;
    public static final int TAMANHO_MAXIMO = 100;

    public HistoricoFiltro {
        tamanho = tamanho == null ? TAMANHO_PADRAO : tamanho;
    }
}
