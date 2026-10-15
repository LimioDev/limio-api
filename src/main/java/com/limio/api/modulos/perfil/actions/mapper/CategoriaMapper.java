package com.limio.api.modulos.perfil.actions.mapper;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.records.CategoriaResponse;

/** {@link Categoria} -> {@link CategoriaResponse}. Chamado só por controller (direto ou via {@link PerfilAtuacaoMapper}). */
@Component
public class CategoriaMapper {

    private static final Comparator<Categoria> ORDEM_EXIBICAO =
            Comparator.comparing(Categoria::getOrdem).thenComparing(Categoria::getNome);

    public CategoriaResponse toResponse(Categoria entity) {
        return new CategoriaResponse(entity.getId(), entity.getNome(), entity.getGrupo());
    }

    /** Sempre em ordem de exibição — as especialidades do perfil vêm de um {@code Set}, sem ordem. */
    public List<CategoriaResponse> toResponse(Collection<Categoria> entities) {
        return entities.stream()
                .sorted(ORDEM_EXIBICAO)
                .map(this::toResponse)
                .toList();
    }
}
