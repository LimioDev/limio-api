package com.limio.api.modulos.anuncio.enums;

/**
 * Ciclo de vida de uma proposta de reagendamento (ETI-99). {@code PENDENTE} é o único estado aberto; os demais são
 * finais:
 * <ul>
 * <li>{@code ACEITO} / {@code RECUSADO}: a contraparte respondeu dentro do prazo;</li>
 * <li>{@code CANCELADO}: o próprio proponente desistiu antes da resposta;</li>
 * <li>{@code EXPIRADO}: o prazo de resposta terminou sem manifestação;</li>
 * <li>{@code SEM_EFEITO}: o serviço deixou de estar combinado (cancelado, iniciado, concluído ou conta encerrada)
 * enquanto a proposta estava pendente.</li>
 * </ul>
 */
public enum StatusReagendamento {
    PENDENTE,
    ACEITO,
    RECUSADO,
    CANCELADO,
    EXPIRADO,
    SEM_EFEITO
}
