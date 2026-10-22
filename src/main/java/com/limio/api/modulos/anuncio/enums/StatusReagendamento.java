package com.limio.api.modulos.anuncio.enums;

/**
 * Diagrama de classes declara o enum sem valores ("a definir pelo grupo").
 * Mínimo pra TICKET-0049: uma proposta pode estar pendente, ter sido
 * respondida (aceita/recusada pela outra parte) ou cancelada sem efeito
 * (conta de uma das partes encerrada enquanto pendente).
 */
public enum StatusReagendamento {
    PENDENTE,
    ACEITO,
    RECUSADO,
    CANCELADO
}
