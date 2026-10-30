package com.limio.api.modulos.anuncio.records;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CandidatoResponse(UUID candidaturaId, String nome, String fotoUrl, Instant fotoExpiraEm,
        List<String> especialidades, boolean gold, int qtdAvaliacoes, BigDecimal mediaAvaliacoes, UUID chatId) {
}
