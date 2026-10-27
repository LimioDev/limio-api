package com.limio.api.modulos.anuncio;

import java.util.UUID;

import com.limio.api.comum.midia.LinkTemporario;

/** Foto de um anúncio com o link temporário de leitura; {@code link} é {@code null} se o storage não respondeu. */
public record FotoDoAnuncio(UUID id, LinkTemporario link) {
}
