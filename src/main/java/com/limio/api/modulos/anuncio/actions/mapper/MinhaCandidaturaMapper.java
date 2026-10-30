package com.limio.api.modulos.anuncio.actions.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.limio.api.comum.records.PageResponse;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.MinhaCandidatura;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.records.MinhaCandidaturaResponse;

@Component
public class MinhaCandidaturaMapper {

    public PageResponse<MinhaCandidaturaResponse> toResponse(Page<MinhaCandidatura> pagina) {
        return PageResponse.de(pagina.map(MinhaCandidaturaMapper::toResponse));
    }

    private static MinhaCandidaturaResponse toResponse(MinhaCandidatura item) {
        Aplicacao aplicacao = item.aplicacao();
        Anuncio anuncio = item.anuncio();
        return new MinhaCandidaturaResponse(
                aplicacao.getId(),
                anuncio.getId(),
                anuncio.getTitulo(),
                anuncio.getDataHoraPretendida(),
                item.nomeEmpregador(),
                item.iniciaisEmpregador(),
                aplicacao.getStatus());
    }
}
