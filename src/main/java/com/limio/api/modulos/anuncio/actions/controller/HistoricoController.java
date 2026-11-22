package com.limio.api.modulos.anuncio.actions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.records.PageResponse;
import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.HistoricoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.ListarHistoricoUseCase;
import com.limio.api.modulos.anuncio.records.HistoricoFiltro;
import com.limio.api.modulos.anuncio.records.HistoricoItemResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/anuncios/historico")
public class HistoricoController {

    private final ListarHistoricoUseCase listarHistoricoUseCase;
    private final HistoricoMapper historicoMapper;

    public HistoricoController(ListarHistoricoUseCase listarHistoricoUseCase, HistoricoMapper historicoMapper) {
        this.listarHistoricoUseCase = listarHistoricoUseCase;
        this.historicoMapper = historicoMapper;
    }

    @GetMapping
    public ResponseEntity<PageResponse<HistoricoItemResponse>> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @ModelAttribute HistoricoFiltro filtro) {
        return ResponseEntity.ok(historicoMapper.toResponse(listarHistoricoUseCase.executar(usuario.id(),
                usuario.papel(), filtro.status(), filtro.dataInicio(), filtro.dataFim(), filtro.pagina(),
                filtro.tamanho())));
    }
}
