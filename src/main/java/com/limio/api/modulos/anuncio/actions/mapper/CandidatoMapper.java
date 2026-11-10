package com.limio.api.modulos.anuncio.actions.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.CandidatoDoAnuncio;
import com.limio.api.modulos.anuncio.records.CandidatoResponse;
import com.limio.api.modulos.anuncio.records.CandidatosResponse;
import com.limio.api.modulos.anuncio.records.EscolhaResponse;

@Component
public class CandidatoMapper {

    public CandidatosResponse toResponse(List<CandidatoDoAnuncio> candidatos) {
        return new CandidatosResponse(candidatos.stream().map(CandidatoMapper::toResponse).toList());
    }

    public EscolhaResponse toEscolhaResponse(Anuncio anuncio) {
        return new EscolhaResponse(anuncio.getId());
    }

    private static CandidatoResponse toResponse(CandidatoDoAnuncio candidato) {
        return new CandidatoResponse(
                candidato.candidaturaId(),
                candidato.nome(),
                candidato.foto() == null ? null : candidato.foto().url(),
                candidato.foto() == null ? null : candidato.foto().expiraEm(),
                candidato.especialidades(),
                candidato.gold(),
                candidato.qtdAvaliacoes(),
                candidato.mediaAvaliacoes(),
                candidato.chatId());
    }
}
