package com.limio.api.modulos.anuncio;

import java.util.UUID;

import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/** Candidatura do próprio Prestador no anúncio: o {@code id} dá o acesso ao chat dela (ETI-101). */
public record MinhaCandidatura(UUID id, StatusAplicacao status) {
}
