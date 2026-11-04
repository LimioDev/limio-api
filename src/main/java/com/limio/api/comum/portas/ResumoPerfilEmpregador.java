package com.limio.api.comum.portas;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code fotoUrl} é um link temporário de leitura, válido até {@code fotoExpiraEm}; ambos {@code null} sem foto.
 * {@code mediaAvaliacoes} é {@code null} enquanto o Empregador não tem avaliações ({@code qtdAvaliacoes == 0}).
 */
public record ResumoPerfilEmpregador(String nomeExibicao, String fotoUrl, Instant fotoExpiraEm,
        BigDecimal mediaAvaliacoes, int qtdAvaliacoes) {
}
