package com.limio.api.modulos.perfil.actions.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.modulos.perfil.actions.mapper.CategoriaMapper;
import com.limio.api.modulos.perfil.actions.usecase.ListarCategoriasUseCase;
import com.limio.api.modulos.perfil.records.CategoriaResponse;

/** Categorias que o Prestador pode escolher como especialidade (TICKET-0054). Exige login, qualquer papel. */
@RestController
@RequestMapping("/categorias")
public class CategoriaController {

    private final ListarCategoriasUseCase listarCategoriasUseCase;
    private final CategoriaMapper categoriaMapper;

    public CategoriaController(ListarCategoriasUseCase listarCategoriasUseCase, CategoriaMapper categoriaMapper) {
        this.listarCategoriasUseCase = listarCategoriasUseCase;
        this.categoriaMapper = categoriaMapper;
    }

    @GetMapping
    public ResponseEntity<List<CategoriaResponse>> listar() {
        return ResponseEntity.ok(categoriaMapper.toResponse(listarCategoriasUseCase.executar()));
    }
}
