package com.limio.api.modulos.anuncio.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.CandidatoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.ListarCandidatosUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.RecusarCandidatoUseCase;
import com.limio.api.modulos.anuncio.records.CandidatosResponse;

@RestController
@RequestMapping("/anuncios/{anuncioId}/candidaturas")
public class CandidatoController {

    private final ListarCandidatosUseCase listarCandidatosUseCase;
    private final RecusarCandidatoUseCase recusarCandidatoUseCase;
    private final CandidatoMapper candidatoMapper;

    public CandidatoController(ListarCandidatosUseCase listarCandidatosUseCase,
            RecusarCandidatoUseCase recusarCandidatoUseCase, CandidatoMapper candidatoMapper) {
        this.listarCandidatosUseCase = listarCandidatosUseCase;
        this.recusarCandidatoUseCase = recusarCandidatoUseCase;
        this.candidatoMapper = candidatoMapper;
    }

    @GetMapping
    public ResponseEntity<CandidatosResponse> listar(@PathVariable UUID anuncioId,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(candidatoMapper.toResponse(listarCandidatosUseCase.executar(usuario.id(), anuncioId)));
    }

    @PostMapping("/{candidaturaId}/recusar")
    public ResponseEntity<CandidatosResponse> recusar(@PathVariable UUID anuncioId, @PathVariable UUID candidaturaId,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        recusarCandidatoUseCase.executar(usuario.id(), anuncioId, candidaturaId);
        return ResponseEntity.ok(candidatoMapper.toResponse(listarCandidatosUseCase.executar(usuario.id(), anuncioId)));
    }
}
