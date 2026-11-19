package com.limio.api.modulos.moderacao.enums;

/**
 * Andamento da denúncia na fila do admin. Só {@code ABERTA} por enquanto (ETI-114): os status da análise e da decisão
 * entram com a fila do admin (US58).
 */
public enum StatusResolucao {
    ABERTA
}
