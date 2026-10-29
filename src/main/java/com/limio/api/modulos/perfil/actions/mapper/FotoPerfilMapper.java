package com.limio.api.modulos.perfil.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.modulos.perfil.records.FotoPerfilResponse;

@Component
public class FotoPerfilMapper {

    public FotoPerfilResponse toResponse(LinkTemporario link) {
        return new FotoPerfilResponse(link.url(), link.expiraEm());
    }
}
