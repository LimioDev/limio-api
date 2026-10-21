package com.limio.api.modulos.perfil.actions.usecase;

import java.util.List;

import org.springframework.stereotype.Service;

import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.categoria.CategoriaService;

/**
 * Regra de negócio do TICKET-0054 (listar categorias): as ativas, em ordem de exibição. "Outros" sempre
 * vem: é protegida, e o banco não deixa categoria protegida ficar inativa. Não conhece HTTP nem record.
 */
@Service
public class ListarCategoriasUseCase {

    private final CategoriaService categoriaService;

    public ListarCategoriasUseCase(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    public List<Categoria> executar() {
        return categoriaService.listarAtivas();
    }
}
