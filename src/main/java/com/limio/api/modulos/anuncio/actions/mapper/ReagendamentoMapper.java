package com.limio.api.modulos.anuncio.actions.mapper;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.anuncio.ReagendamentoConsultado;
import com.limio.api.modulos.anuncio.records.ReagendamentoResponse;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;

@Component
public class ReagendamentoMapper {

    public ReagendamentoResponse toResponse(Reagendamento reagendamento) {
        return toResponse(reagendamento, reagendamento.getDataAnterior());
    }

    public ReagendamentoResponse toResponse(ReagendamentoConsultado consultado) {
        return toResponse(consultado.reagendamento(), consultado.dataVigente());
    }

    private ReagendamentoResponse toResponse(Reagendamento reagendamento, Instant dataVigente) {
        return new ReagendamentoResponse(
                reagendamento.getId(),
                reagendamento.getAnuncioId(),
                reagendamento.getStatus(),
                dataVigente,
                reagendamento.getDataAnterior(),
                reagendamento.getDataProposta(),
                reagendamento.getJustificativa(),
                reagendamento.getProponenteId(),
                reagendamento.getCriadoEm(),
                reagendamento.getExpiraEm(),
                reagendamento.getRespondidoEm());
    }
}
