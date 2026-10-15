package com.limio.api.modulos.perfil.records;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.enums.DiaSemana;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** Uma janela de atendimento — horários em {@code HH:mm}. */
public record JanelaAtendimentoRequest(
        @NotNull(message = "{janelaAtendimento.diaSemana.obrigatorio}")
        DiaSemana diaSemana,

        @NotNull(message = "{janelaAtendimento.horaInicio.obrigatoria}")
        LocalTime horaInicio,

        @NotNull(message = "{janelaAtendimento.horaFim.obrigatoria}")
        LocalTime horaFim) {

    /**
     * Início antes do fim (card). Horário ausente já é recusado pelo {@code @NotNull} do campo — aqui não
     * acusa de novo. {@code @JsonIgnore}: é regra de validação, não campo do contrato.
     */
    @JsonIgnore
    @AssertTrue(message = "{janelaAtendimento.intervalo}")
    public boolean isIntervaloValido() {
        return horaInicio == null || horaFim == null || horaInicio.isBefore(horaFim);
    }
}
