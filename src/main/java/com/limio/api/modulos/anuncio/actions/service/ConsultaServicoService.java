package com.limio.api.modulos.anuncio.actions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaServico;
import com.limio.api.comum.portas.ResumoServico;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;

/** Implementa {@link ConsultaServico} para a avaliação entre as partes ({@code modulos.moderacao}, ETI-107). */
@Component
public class ConsultaServicoService implements ConsultaServico {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;

    public ConsultaServicoService(AnuncioService anuncioService, AplicacaoService aplicacaoService) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumoServico> buscarPorId(UUID anuncioId) {
        return anuncioService.buscarPorId(anuncioId)
                .map(anuncio -> new ResumoServico(anuncio.getId(), anuncio.getTitulo(), anuncio.getContratanteId(),
                        aplicacaoService.buscarPrestadorDoServico(anuncio.getId()).orElse(null),
                        anuncio.concluidoEm()));
    }
}
