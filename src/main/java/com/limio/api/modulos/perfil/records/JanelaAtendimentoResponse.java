package com.limio.api.modulos.perfil.records;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.enums.DiaSemana;

/** Horários em {@code HH:mm}, mesmo formato da entrada (sem isso o Jackson devolveria {@code HH:mm:ss}). */
public record JanelaAtendimentoResponse(
        DiaSemana diaSemana,
        @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
        @JsonFormat(pattern = "HH:mm") LocalTime horaFim) {
}
