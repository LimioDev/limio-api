package com.limio.api.modulos.chat.records;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Query de {@code GET /chats} (ETI-103). {@code pagina} começa em 0; {@code tamanho} fora de 1 a
 * {@value #TAMANHO_MAXIMO} responde 400 {@code VALIDACAO_FALHOU}. Os dois são opcionais.
 */
public record ConversasFiltro(
        @PositiveOrZero(message = "{chat.conversas.pagina}")
        Integer pagina,
        @Min(value = 1, message = "{chat.conversas.tamanho}")
        @Max(value = ConversasFiltro.TAMANHO_MAXIMO, message = "{chat.conversas.tamanho}")
        Integer tamanho) {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 50;

    public ConversasFiltro {
        pagina = pagina == null ? 0 : pagina;
        tamanho = tamanho == null ? TAMANHO_PADRAO : tamanho;
    }
}
