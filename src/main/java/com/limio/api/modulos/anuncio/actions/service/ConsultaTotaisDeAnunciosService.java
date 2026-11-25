package com.limio.api.modulos.anuncio.actions.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaTotaisDeAnuncios;
import com.limio.api.comum.portas.TotaisDeAnuncios;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;

/** Implementa {@link ConsultaTotaisDeAnuncios} para o dashboard do administrador (ETI-117). */
@Component
public class ConsultaTotaisDeAnunciosService implements ConsultaTotaisDeAnuncios {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;

    public ConsultaTotaisDeAnunciosService(AnuncioService anuncioService, AplicacaoService aplicacaoService) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
    }

    @Override
    @Transactional(readOnly = true)
    public TotaisDeAnuncios contar() {
        return new TotaisDeAnuncios(anuncioService.contar(), aplicacaoService.contar(),
                aplicacaoService.contarEscolhidas());
    }
}
