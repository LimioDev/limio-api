package com.limio.api.modulos.anuncio;

import java.time.Instant;

import com.limio.api.modulos.anuncio.enums.StatusHistorico;

/**
 * Filtros do histórico de anúncios (ETI-71), já em tipos de domínio. Todos opcionais ({@code null} = sem filtro):
 * {@code status} ausente traz os três; o período vale para a data do desfecho do anúncio
 * ({@link Anuncio#desfechoEm()}), com {@code de} inclusivo e {@code ate} exclusivo.
 */
public record CriterioDoHistorico(StatusHistorico status, Instant de, Instant ate) {

    public CriterioDoHistorico {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new IllegalArgumentException("início do período depois do fim");
        }
    }
}
