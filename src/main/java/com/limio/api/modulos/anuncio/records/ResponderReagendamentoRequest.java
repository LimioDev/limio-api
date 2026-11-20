package com.limio.api.modulos.anuncio.records;

import com.limio.api.modulos.anuncio.enums.DecisaoReagendamento;

import jakarta.validation.constraints.NotNull;

public record ResponderReagendamentoRequest(
        @NotNull(message = "{anuncio.reagendamento.decisao.obrigatoria}")
        DecisaoReagendamento decisao) {
}
