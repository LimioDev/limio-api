package com.limio.api.comum.persistencia.enums;

/**
 * Namespace de cada advisory lock do projeto ({@code TravaTransacional}). Lista central de propósito: dá
 * pra auditar aqui todos os locks em uso, e dois escopos diferentes nunca disputam a mesma chave.
 */
public enum EscopoTrava {

    /** Tentativas de login por e-mail (TICKET-0032). */
    LOGIN,

    /** Tentativas de login por IP. Sempre travado depois de {@link #LOGIN}, nunca antes (sem deadlock). */
    LOGIN_IP,

    /** Salvamento do perfil de atuação por usuário (TICKET-0054). */
    PERFIL_PRESTADOR,

    /** Troca da foto de perfil por usuário (TICKET-0056). */
    FOTO_PERFIL,

    /** Envio de fotos por anúncio, pro limite de quantidade (ETI-59). */
    FOTOS_ANUNCIO,

    /**
     * Mudança do conteúdo ou do estado de um anúncio: editar, recusar candidato, anexar/remover foto
     * (ETI-61) e validar o serviço (ETI-92). {@code CancelarAnuncioUseCase} e {@code IniciarServicoUseCase}
     * ainda não tomam esta trava (o segundo usa lock de linha); escolher candidato (ETI-69/70) deve tomar
     * também. Quando um usecase também trava {@link #FOTOS_ANUNCIO}, trava esta primeiro (sem deadlock).
     */
    ANUNCIO
}
