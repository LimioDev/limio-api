package com.limio.api.modulos.anuncio.actions.mapper;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.anuncio.ReagendamentoConsultado;
import com.limio.api.modulos.anuncio.records.ReagendamentoResponse;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;

@Component
public class ReagendamentoMapper {

    /** A data vigente vem do usecase (lida do anúncio sob a trava): o mapper só traduz, não deduz regra. */
    public ReagendamentoResponse toResponse(ReagendamentoConsultado consultado) {
        Reagendamento reagendamento = consultado.reagendamento();
        Instant dataVigente = consultado.dataVigente();
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
