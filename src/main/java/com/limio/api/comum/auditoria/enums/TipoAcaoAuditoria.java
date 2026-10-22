package com.limio.api.comum.auditoria.enums;

/**
 * Valor fechado de ação auditável. Diagrama de classes e DER não definem os
 * valores ("a definir pelo grupo", nota do próprio diagrama) — cresce um
 * valor por vez, conforme a ação que passar a ser registrada.
 */
public enum TipoAcaoAuditoria {
    CONTA_ENCERRADA,
    COMBINADO_CANCELADO
}
