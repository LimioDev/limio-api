package com.limio.api.comum.portas;

import java.util.UUID;

/**
 * Quem está envolvido num anúncio, para decidir quem pode denunciar por ele (ETI-114). {@code escolhidoId} é o
 * Prestador que já foi escolhido alguma vez, mesmo que depois o serviço tenha sido cancelado ou o anúncio encerrado
 * ({@code escolhidaEm} é permanente), e {@code null} se ninguém nunca foi escolhido. {@code noMural} diz se o anúncio
 * ainda está {@code PUBLICADO}.
 */
public record PartesAnuncio(UUID anuncioId, UUID contratanteId, UUID escolhidoId, boolean noMural) {

    public boolean teveEscolhido() {
        return escolhidoId != null;
    }

    /** Empregador dono do anúncio ou Prestador que foi escolhido. */
    public boolean participa(UUID usuarioId) {
        return contratanteId.equals(usuarioId) || usuarioId.equals(escolhidoId);
    }

    /** Quem está do outro lado para {@code usuarioId}, que precisa ser uma das partes. */
    public UUID outraParte(UUID usuarioId) {
        return contratanteId.equals(usuarioId) ? escolhidoId : contratanteId;
    }
}
