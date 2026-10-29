package com.limio.api.modulos.anuncio.actions.controller;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.records.PageResponse;
import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.MuralMapper;
import com.limio.api.modulos.anuncio.actions.usecase.ListarMuralUseCase;
import com.limio.api.modulos.anuncio.enums.QuandoMural;
import com.limio.api.modulos.anuncio.records.MuralItemResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/anuncios/mural")
public class MuralController {

    private final ListarMuralUseCase listarMuralUseCase;
    private final MuralMapper muralMapper;

    public MuralController(ListarMuralUseCase listarMuralUseCase, MuralMapper muralMapper) {
        this.listarMuralUseCase = listarMuralUseCase;
        this.muralMapper = muralMapper;
    }

    @GetMapping
    public ResponseEntity<PageResponse<MuralItemResponse>> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Set<UUID> categorias,
            @RequestParam(required = false) @Min(1) @Max(40) Integer raio,
            @RequestParam(defaultValue = "qualquer") QuandoMural quando,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int tamanho) {
        return ResponseEntity.ok(muralMapper.toResponse(listarMuralUseCase.executar(
                usuario.id(), usuario.papel(), categorias, raio, quando, pagina, tamanho)));
    }
}
