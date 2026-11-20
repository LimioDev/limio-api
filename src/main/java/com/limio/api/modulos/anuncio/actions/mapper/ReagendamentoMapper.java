package com.limio.api.modulos.anuncio.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.anuncio.records.ReagendamentoResponse;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;

@Component
public class ReagendamentoMapper {

    public ReagendamentoResponse toResponse(Reagendamento reagendamento) {
        return new ReagendamentoResponse(
                reagendamento.getId(),
                reagendamento.getAnuncioId(),
                reagendamento.getStatus(),
                reagendamento.getDataAnterior(),
                reagendamento.getDataProposta(),
                reagendamento.getJustificativa(),
                reagendamento.getProponenteId(),
                reagendamento.getCriadoEm(),
                reagendamento.getExpiraEm());
    }
}
