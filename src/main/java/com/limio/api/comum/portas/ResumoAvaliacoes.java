package com.limio.api.comum.portas;

import java.math.BigDecimal;

public record ResumoAvaliacoes(int quantidade, BigDecimal media) {

    public static final ResumoAvaliacoes SEM_AVALIACOES = new ResumoAvaliacoes(0, null);

    public ResumoAvaliacoes {
        if (quantidade < 0) {
            throw new IllegalArgumentException("quantidade de avaliações negativa");
        }
        if (quantidade == 0) {
            media = null;
        } else if (media == null) {
            throw new IllegalArgumentException("média ausente com avaliações");
        }
    }
}
