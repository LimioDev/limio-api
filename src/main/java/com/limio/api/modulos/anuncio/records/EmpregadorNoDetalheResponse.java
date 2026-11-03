package com.limio.api.modulos.anuncio.records;

import java.math.BigDecimal;

public record EmpregadorNoDetalheResponse(String nomeExibicao, String fotoUrl, BigDecimal mediaAvaliacoes,
        int qtdAvaliacoes) {
}
