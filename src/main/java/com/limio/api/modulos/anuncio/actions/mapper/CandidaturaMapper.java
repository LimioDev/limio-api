package com.limio.api.modulos.anuncio.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.records.CandidaturaResponse;

@Component
public class CandidaturaMapper {

    public CandidaturaResponse toResponse(Aplicacao aplicacao) {
        return new CandidaturaResponse(aplicacao.getId(), aplicacao.getStatus());
    }
}
