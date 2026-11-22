package com.limio.api.modulos.moderacao.enums;

/**
 * O que foi denunciado (ETI-116), deduzido do contexto gravado na denúncia: a mensagem é o alvo mais específico, depois
 * a candidatura, depois o anúncio. Anúncio com Prestador escolhido é {@code SERVICO}. Sem contexto, é o perfil.
 */
public enum TipoAlvoDenuncia {
    PERFIL,
    ANUNCIO,
    SERVICO,
    CANDIDATURA,
    MENSAGEM
}
