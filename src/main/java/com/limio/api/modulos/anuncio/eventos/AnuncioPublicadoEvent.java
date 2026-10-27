package com.limio.api.modulos.anuncio.eventos;

import java.util.UUID;

/**
 * Publicado quando um anúncio é criado e vai pro mural (ETI-59). Ninguém escuta ainda: o aviso aos prestadores
 * compatíveis é o ETI-65, que deve reagir em {@code AFTER_COMMIT} (ADR-0003). Interno ao {@code anuncio}.
 */
public record AnuncioPublicadoEvent(UUID anuncioId) {
}
