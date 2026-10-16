package com.limio.api.modulos.perfil.records;

import java.math.BigDecimal;
import java.time.Instant;

public record PerfilEmpregadorResponse(
        String nomeExibicao,
        BigDecimal mediaAvaliacoes,
        int qtdAvaliacoes,
        String email,
        String telefone,
        Instant emailConfirmadoEm,
        Instant telefoneConfirmadoEm) {
}
