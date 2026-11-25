package com.limio.api.modulos.anuncio.records;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProporReagendamentoRequest(
        @NotNull(message = "{anuncio.reagendamento.novaDataHora.obrigatoria}")
        Instant novaDataHora,
        @NotBlank(message = "{anuncio.reagendamento.justificativa.obrigatoria}")
        @Size(max = 600, message = "{anuncio.reagendamento.justificativa.tamanho}")
        String justificativa) {
}
