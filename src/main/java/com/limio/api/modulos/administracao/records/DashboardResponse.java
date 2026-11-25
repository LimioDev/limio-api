package com.limio.api.modulos.administracao.records;

import java.util.Objects;

import com.limio.api.modulos.administracao.enums.StatusOperacional;

/**
 * Resposta de {@code GET /admin/dashboard} (ETI-117): os seis cartões do painel e a linha do status operacional.
 * Todo contador é inteiro maior ou igual a zero.
 *
 * @param statusOperacional          código fechado, pra o painel decidir a cor
 * @param statusOperacionalDescricao texto curto exibido ("Operando normalmente")
 */
public record DashboardResponse(long usuarios, long anuncios, long candidaturas, long escolhas,
        long assinaturasGoldAtivas, long denunciasAbertas, StatusOperacional statusOperacional,
        String statusOperacionalDescricao) {

    public DashboardResponse {
        naoNegativo(usuarios, "usuarios");
        naoNegativo(anuncios, "anuncios");
        naoNegativo(candidaturas, "candidaturas");
        naoNegativo(escolhas, "escolhas");
        naoNegativo(assinaturasGoldAtivas, "assinaturasGoldAtivas");
        naoNegativo(denunciasAbertas, "denunciasAbertas");
        Objects.requireNonNull(statusOperacional, "statusOperacional");
        Objects.requireNonNull(statusOperacionalDescricao, "statusOperacionalDescricao");
    }

    private static void naoNegativo(long valor, String campo) {
        if (valor < 0) {
            throw new IllegalArgumentException(campo + " não pode ser negativo");
        }
    }
}
