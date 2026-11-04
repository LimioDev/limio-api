package com.limio.api.modulos.anuncio;

import java.util.UUID;

import com.limio.api.comum.midia.LinkTemporario;

/** Foto do check-out com o link temporário de leitura; {@code link} é {@code null} se o storage não respondeu (ETI-92). */
public record FotoDoCheckout(UUID id, LinkTemporario link) {
}
