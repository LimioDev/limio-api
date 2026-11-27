package com.limio.api.modulos.anuncio.enums;

/**
 * Por que um anúncio foi para {@link StatusAnuncio#ENCERRADO} (ETI-71). Existe porque "cancelado" e "expirado" não
 * têm status próprio: sem isto, só dava para distingui-los pelo texto livre de {@code motivoEncerramento}.
 * Só um anúncio {@code ENCERRADO} tem causa; nos demais status ela é {@code null}.
 */
public enum CausaEncerramento {
    CANCELAMENTO,
    EXPIRACAO
}
