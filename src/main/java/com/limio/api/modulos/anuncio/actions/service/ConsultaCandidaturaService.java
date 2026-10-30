package com.limio.api.modulos.anuncio.actions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ResumoCandidatura;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;

/** Implementa {@link ConsultaCandidatura} para o cabeçalho do chat ({@code modulos.chat}, ETI-101). */
@Component
public class ConsultaCandidaturaService implements ConsultaCandidatura {

    private final AplicacaoService aplicacaoService;
    private final AnuncioService anuncioService;
    private final ConsultaCategoria consultaCategoria;

    public ConsultaCandidaturaService(AplicacaoService aplicacaoService, AnuncioService anuncioService,
            ConsultaCategoria consultaCategoria) {
        this.aplicacaoService = aplicacaoService;
        this.anuncioService = anuncioService;
        this.consultaCategoria = consultaCategoria;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumoCandidatura> buscarPorId(UUID candidaturaId) {
        return aplicacaoService.buscarPorId(candidaturaId)
                .flatMap(aplicacao -> anuncioService.buscarPorId(aplicacao.getAnuncioId())
                        .map(anuncio -> new ResumoCandidatura(aplicacao.getId(), aplicacao.getStatus().name(),
                                anuncio.getId(), anuncio.getContratanteId(), aplicacao.getPrestadorId(),
                                anuncio.getTitulo(), nomeDaCategoria(anuncio.getCategoriaId()))));
    }

    // Categoria nunca é apagada, só desativada: ausência aqui é dado corrompido, não cenário de negócio.
    private String nomeDaCategoria(UUID categoriaId) {
        return consultaCategoria.buscarPorId(categoriaId)
                .map(ResumoCategoria::nome)
                .orElseThrow(() -> new IllegalStateException("categoria do anúncio não existe: " + categoriaId));
    }
}
