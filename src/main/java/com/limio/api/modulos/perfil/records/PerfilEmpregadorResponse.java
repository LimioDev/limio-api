package com.limio.api.modulos.perfil.records;

import java.math.BigDecimal;

/**
 * Parte do perfil que pertence ao módulo {@code perfil}. {@code mediaAvaliacoes} é
 * {@code null} sem avaliação (RB11); {@code qtdAvaliacoes} é contagem real (0).
 * Foto, resumo de endereços e dados de contato entram quando Midia, endereco_salvo
 * e a leitura de contato do auth existirem (TICKET-0048).
 */
public record PerfilEmpregadorResponse(
        String nomeExibicao,
        BigDecimal mediaAvaliacoes,
        int qtdAvaliacoes) {
}
