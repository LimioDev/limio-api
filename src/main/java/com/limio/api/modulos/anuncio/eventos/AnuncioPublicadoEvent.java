package com.limio.api.modulos.anuncio.eventos;

import java.util.UUID;

/**
 * Publicado quando um anúncio é criado e vai pro mural (ETI-59). Escutado em {@code AFTER_COMMIT} por {@code
 * AvisarPrestadoresCompativeisUseCase}, que pede o aviso aos Prestadores compatíveis (ETI-65). Interno ao {@code
 * anuncio}.
 */
public record AnuncioPublicadoEvent(UUID anuncioId) {
}
