package com.limio.api.modulos.perfil.actions.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.records.CategoriaResponse;

/** {@link Categoria} -> {@link CategoriaResponse}. Chamado só por controller (direto ou via {@link PerfilAtuacaoMapper}). */
@Component
public class CategoriaMapper {

    public CategoriaResponse toResponse(Categoria entity) {
        return new CategoriaResponse(entity.getId(), entity.getNome(), entity.getGrupo());
    }

    /** Mantém a ordem recebida — {@code GET /categorias} já vem ordenado do banco. */
    public List<CategoriaResponse> toResponse(List<Categoria> entities) {
        return entities.stream()
                .map(this::toResponse)
                .toList();
    }
}
