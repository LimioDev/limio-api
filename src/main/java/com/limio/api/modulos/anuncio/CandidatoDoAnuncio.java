package com.limio.api.modulos.anuncio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.limio.api.comum.midia.LinkTemporario;

/** {@code usuarioId} é o do Prestador candidato: o app abre o perfil público dele (ETI-136). */
public record CandidatoDoAnuncio(UUID candidaturaId, UUID usuarioId, String nome, LinkTemporario foto,
        List<String> especialidades, boolean gold, int qtdAvaliacoes, BigDecimal mediaAvaliacoes, UUID chatId) {
}
