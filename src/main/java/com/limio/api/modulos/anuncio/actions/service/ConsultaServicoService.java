package com.limio.api.modulos.anuncio.actions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaServico;
import com.limio.api.comum.portas.PartesAnuncio;
import com.limio.api.comum.portas.ResumoServico;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;

/**
 * Implementa {@link ConsultaServico} para a avaliação entre as partes (ETI-107) e a denúncia (ETI-114), ambas de
 * {@code modulos.moderacao}.
 */
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

    @Override
    @Transactional(readOnly = true)
    public Optional<PartesAnuncio> buscarPartes(UUID anuncioId) {
        return anuncioService.buscarPorId(anuncioId)
                .map(anuncio -> new PartesAnuncio(anuncio.getId(), anuncio.getContratanteId(),
                        aplicacaoService.buscarEscolhidaDoAnuncio(anuncio.getId())
                                .map(Aplicacao::getPrestadorId).orElse(null),
                        anuncio.getStatus() == StatusAnuncio.PUBLICADO));
    }
}
