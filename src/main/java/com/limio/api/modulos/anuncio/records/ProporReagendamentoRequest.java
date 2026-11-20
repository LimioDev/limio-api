package com.limio.api.modulos.anuncio.records;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProporReagendamentoRequest(
        @NotNull(message = "{anuncio.reagendamento.novaDataHora.obrigatoria}")
        Instant novaDataHora,
        @NotBlank(message = "{anuncio.reagendamento.justificativa.obrigatoria}")
        String justificativa) {
}
