package com.limio.api.modulos.perfil;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.limio.api.comum.portas.AvaliacaoRecente;

/**
 * Perfil público e reputação de um usuário (ETI-105) — resultado do {@code
 * ConsultarPerfilPublicoUseCase}, antes de virar contrato HTTP. {@code especialidades} só é preenchida pro
 * papel Prestador; vazia pro Empregador. {@code mediaAvaliacoes} é {@code null} enquanto {@code
 * qtdAvaliacoes == 0} (UC "Exibir média somente com avaliações") — nunca {@code 0,0}.
 */
public record PerfilPublico(
        String nomeExibicao,
        String fotoUrl,
        Instant fotoExpiraEm,
        BigDecimal mediaAvaliacoes,
        int qtdAvaliacoes,
        int servicosConcluidos,
        List<String> especialidades,
        List<AvaliacaoRecente> avaliacoesRecentes) {

    public PerfilPublico {
        especialidades = List.copyOf(especialidades);
        avaliacoesRecentes = List.copyOf(avaliacoesRecentes);
    }
}
