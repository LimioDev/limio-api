package com.limio.api.modulos.anuncio.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.records.ServicoIniciadoResponse;

@Component
public class ServicoMapper {

    public ServicoIniciadoResponse toIniciadoResponse(Anuncio anuncio) {
        return new ServicoIniciadoResponse(anuncio.getStatus(), anuncio.getIniciadoEm());
    }
}
