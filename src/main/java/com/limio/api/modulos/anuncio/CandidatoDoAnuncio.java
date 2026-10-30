package com.limio.api.modulos.anuncio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.limio.api.comum.midia.LinkTemporario;

public record CandidatoDoAnuncio(UUID candidaturaId, String nome, LinkTemporario foto, List<String> especialidades,
        boolean gold, int qtdAvaliacoes, BigDecimal mediaAvaliacoes, UUID chatId) {
}
