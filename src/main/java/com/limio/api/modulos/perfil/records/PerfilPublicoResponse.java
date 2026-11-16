package com.limio.api.modulos.perfil.records;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * {@code mediaAvaliacoes} é {@code null} enquanto {@code qtdAvaliacoes == 0} — o front mostra "sem
 * avaliações", nunca {@code 0,0}. {@code especialidades} vem vazia pro papel Empregador.
 */
public record PerfilPublicoResponse(
        String nomeExibicao,
        String fotoUrl,
        Instant fotoExpiraEm,
        BigDecimal mediaAvaliacoes,
        int qtdAvaliacoes,
        int servicosConcluidos,
        List<String> especialidades,
        List<AvaliacaoRecenteResponse> avaliacoesRecentes) {
}
