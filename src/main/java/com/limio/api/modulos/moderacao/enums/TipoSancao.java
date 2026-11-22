package com.limio.api.modulos.moderacao.enums;

/**
 * Sanção aplicada ao denunciado numa decisão procedente (ETI-116). A API aceita os quatro tipos, mesmo que o painel
 * v1 ofereça só advertência e suspensão.
 */
public enum TipoSancao {

    /** Só registra a sanção. */
    ADVERTENCIA,

    /** Oculta uma avaliação que o denunciado escreveu; a média do avaliado é recalculada sem ela. */
    OCULTAR_AVALIACAO,

    /** Conta bloqueada entre início e fim, sessões encerradas e compromissos abertos encerrados. */
    SUSPENSAO,

    /** Mesmo encerramento do pedido do titular (ETI-36): anonimização e CPF bloqueado. */
    ENCERRAMENTO_DE_CONTA
}
