package com.limio.api.modulos.anuncio;

import java.util.List;

import com.limio.api.comum.portas.ResumoCategoria;

/**
 * Anúncio visto pelo dono (tela 16, ETI-59). {@code candidatos} conta as candidaturas {@code ENVIADA};
 * {@code primeirosCandidatos} são os mais antigos delas.
 */
public record MeuAnuncio(
        Anuncio anuncio,
        ResumoCategoria categoria,
        List<FotoDoAnuncio> fotos,
        int candidatos,
        List<CandidatoResumido> primeirosCandidatos) {
}
