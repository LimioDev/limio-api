package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/**
 * {@code fotoUrl} sai sempre {@code null} até o anúncio ganhar foto (card de criar anúncio).
 * {@code prestadorEscolhido} vem abreviado ("Rafael S.") e é {@code null} enquanto ninguém foi escolhido.
 */
public record AnuncioPainelResponse(
        UUID id,
        String fotoUrl,
        String descricao,
        Instant dataHoraPretendida,
        String bairro,
        StatusAnuncio status,
        int candidatos,
        String prestadorEscolhido) {
}
