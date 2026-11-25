package com.limio.api.modulos.moderacao.enums;

/**
 * Andamento da denúncia na fila do admin. Nasce {@code ABERTA} (ETI-114) e sai da fila quando o administrador decide
 * (ETI-116): {@code PROCEDENTE} ou {@code IMPROCEDENTE}, o mesmo valor do {@link VereditoModeracao}.
 */
public enum StatusResolucao {
    ABERTA,
    PROCEDENTE,
    IMPROCEDENTE;

    public static StatusResolucao de(VereditoModeracao veredito) {
        return switch (veredito) {
            case PROCEDENTE -> PROCEDENTE;
            case IMPROCEDENTE -> IMPROCEDENTE;
        };
    }

    /**
     * A denúncia ainda espera uma decisão. Uma igual (mesmo denunciante, alvo e motivo) nesse estado não é gravada de
     * novo. {@code switch} sem {@code default}: status novo obriga a decidir aqui.
     */
    public boolean isEmAberto() {
        return switch (this) {
            case ABERTA -> true;
            case PROCEDENTE, IMPROCEDENTE -> false;
        };
    }
}
