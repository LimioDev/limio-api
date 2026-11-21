package com.limio.api.modulos.anuncio.actions.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaCandidatura;
import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ResumoCandidatura;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;

/**
 * Implementa {@link ConsultaCandidatura} para o cabeçalho do chat ({@code modulos.chat}, ETI-101) e os avisos da
 * escolha ({@code modulos.notificacao}, ETI-112).
 */
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

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarPrestadoresIds(Collection<UUID> candidaturaIds) {
        return aplicacaoService.buscarPrestadoresIds(candidaturaIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ResumoCandidatura> buscarResumosEmLote(Collection<UUID> candidaturaIds) {
        List<Aplicacao> aplicacoes = aplicacaoService.buscarPorIds(candidaturaIds);
        Map<UUID, Anuncio> anuncios = anuncioService
                .buscarPorIds(aplicacoes.stream().map(Aplicacao::getAnuncioId).distinct().toList()).stream()
                .collect(Collectors.toMap(Anuncio::getId, Function.identity()));
        Map<UUID, ResumoCategoria> categorias = consultaCategoria.buscarPorIdsEmLote(
                anuncios.values().stream().map(Anuncio::getCategoriaId).distinct().toList());

        Map<UUID, ResumoCandidatura> resumos = new HashMap<>();
        for (Aplicacao aplicacao : aplicacoes) {
            Anuncio anuncio = anuncios.get(aplicacao.getAnuncioId());
            if (anuncio != null) {
                resumos.put(aplicacao.getId(), new ResumoCandidatura(aplicacao.getId(), aplicacao.getStatus().name(),
                        anuncio.getId(), anuncio.getContratanteId(), aplicacao.getPrestadorId(), anuncio.getTitulo(),
                        nomeDaCategoria(categorias, anuncio.getCategoriaId())));
            }
        }
        return resumos;
    }

    private String nomeDaCategoria(Map<UUID, ResumoCategoria> categorias, UUID categoriaId) {
        ResumoCategoria categoria = categorias.get(categoriaId);
        if (categoria == null) {
            throw new IllegalStateException("categoria do anúncio não existe: " + categoriaId);
        }
        return categoria.nome();
    }

    // Categoria nunca é apagada, só desativada: ausência aqui é dado corrompido, não cenário de negócio.
    private String nomeDaCategoria(UUID categoriaId) {
        return consultaCategoria.buscarPorId(categoriaId)
                .map(ResumoCategoria::nome)
                .orElseThrow(() -> new IllegalStateException("categoria do anúncio não existe: " + categoriaId));
    }
}
