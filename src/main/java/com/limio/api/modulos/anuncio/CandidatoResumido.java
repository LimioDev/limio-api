package com.limio.api.modulos.anuncio;

import com.limio.api.comum.midia.LinkTemporario;

/** Um dos primeiros candidatos, como aparece pro dono do anúncio: iniciais e foto ({@code null} sem foto). */
public record CandidatoResumido(String iniciais, LinkTemporario foto) {
}
