package com.limio.api.modulos.perfil.actions.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ResumoCategoria;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.categoria.CategoriaService;

/** Implementa {@link ConsultaCategoria} para a criação e a consulta de anúncio ({@code modulos.anuncio}, ETI-59). */
@Component
public class ConsultaCategoriaService implements ConsultaCategoria {

    private final CategoriaService categoriaService;

    public ConsultaCategoriaService(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumoCategoria> buscarPorId(UUID categoriaId) {
        return categoriaService.buscarPorId(categoriaId).map(ConsultaCategoriaService::resumoDe);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ResumoCategoria> buscarPorIdsEmLote(Collection<UUID> categoriaIds) {
        if (categoriaIds.isEmpty()) {
            return Map.of();
        }
        return categoriaService.buscarPorIds(categoriaIds).stream()
                .map(ConsultaCategoriaService::resumoDe)
                .collect(Collectors.toMap(ResumoCategoria::id, Function.identity()));
    }

    private static ResumoCategoria resumoDe(Categoria categoria) {
        return new ResumoCategoria(categoria.getId(), categoria.getNome(), categoria.getGrupo(), categoria.isAtiva());
    }
}
