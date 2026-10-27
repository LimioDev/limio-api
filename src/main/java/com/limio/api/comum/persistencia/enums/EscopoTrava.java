package com.limio.api.comum.persistencia.enums;

/**
 * Namespace de cada advisory lock do projeto ({@code TravaTransacional}). Lista central de propósito: dá
 * pra auditar aqui todos os locks em uso, e dois escopos diferentes nunca disputam a mesma chave.
 */
public enum EscopoTrava {

    /** Tentativas de login por e-mail (TICKET-0032). */
    LOGIN,

    /** Salvamento do perfil de atuação por usuário (TICKET-0054). */
    PERFIL_PRESTADOR,

    /** Troca da foto de perfil por usuário (TICKET-0056). */
    FOTO_PERFIL,

    /** Envio de fotos por anúncio, pro limite de quantidade (ETI-59). */
    FOTOS_ANUNCIO
}
