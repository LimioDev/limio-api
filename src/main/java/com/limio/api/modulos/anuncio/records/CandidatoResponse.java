package com.limio.api.modulos.anuncio.records;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Um candidato da tela 18. {@code usuarioId} é o do Prestador, o mesmo que {@code GET /perfis-publicos/{usuarioId}}
 * aceita com {@code papel=PRESTADOR} (ETI-136). Sem contato do candidato.
 */
public record CandidatoResponse(UUID candidaturaId, UUID usuarioId, String nome, String fotoUrl, Instant fotoExpiraEm,
        List<String> especialidades, boolean gold, int qtdAvaliacoes, BigDecimal mediaAvaliacoes, UUID chatId) {
}
