package com.limio.api.modulos.perfil.actions.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaCategoria;
import com.limio.api.comum.portas.ResumoCategoria;
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
        return categoriaService.buscarPorId(categoriaId)
                .map(categoria -> new ResumoCategoria(categoria.getId(), categoria.getNome(), categoria.getGrupo(),
                        categoria.isAtiva()));
    }
}
