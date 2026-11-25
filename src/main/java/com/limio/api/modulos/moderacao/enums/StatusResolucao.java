package com.limio.api.modulos.moderacao.enums;

/**
 * Andamento da denúncia na fila do admin. Só {@code ABERTA} por enquanto (ETI-114): os status da análise e da decisão
 * entram com a fila do admin (US58).
 */
public enum StatusResolucao {
    ABERTA;

    /**
     * A denúncia ainda espera uma decisão. Uma igual (mesmo denunciante, alvo e motivo) nesse estado não é gravada de
     * novo. {@code switch} sem {@code default}: status novo obriga a decidir aqui.
     */
    public boolean isEmAberto() {
        return switch (this) {
            case ABERTA -> true;
        };
    }
}
