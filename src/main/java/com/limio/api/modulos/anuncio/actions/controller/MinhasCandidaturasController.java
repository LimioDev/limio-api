package com.limio.api.modulos.anuncio.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.records.PageResponse;
import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.MinhaCandidaturaMapper;
import com.limio.api.modulos.anuncio.actions.usecase.DesistirCandidaturaUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.ListarMinhasCandidaturasUseCase;
import com.limio.api.modulos.anuncio.records.MinhaCandidaturaResponse;
import com.limio.api.modulos.anuncio.records.MinhasCandidaturasFiltro;

import jakarta.validation.Valid;

/** Tela "Aplicações" do Prestador: listar por aba e desistir de uma pendente (ETI-77). */
@RestController
@RequestMapping("/candidaturas")
public class MinhasCandidaturasController {

    private final ListarMinhasCandidaturasUseCase listarMinhasCandidaturasUseCase;
    private final DesistirCandidaturaUseCase desistirCandidaturaUseCase;
    private final MinhaCandidaturaMapper minhaCandidaturaMapper;

    public MinhasCandidaturasController(ListarMinhasCandidaturasUseCase listarMinhasCandidaturasUseCase,
            DesistirCandidaturaUseCase desistirCandidaturaUseCase, MinhaCandidaturaMapper minhaCandidaturaMapper) {
        this.listarMinhasCandidaturasUseCase = listarMinhasCandidaturasUseCase;
        this.desistirCandidaturaUseCase = desistirCandidaturaUseCase;
        this.minhaCandidaturaMapper = minhaCandidaturaMapper;
    }

    @GetMapping("/minhas")
    public ResponseEntity<PageResponse<MinhaCandidaturaResponse>> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @ModelAttribute MinhasCandidaturasFiltro filtro) {
        return ResponseEntity.ok(minhaCandidaturaMapper.toResponse(
                listarMinhasCandidaturasUseCase.executar(usuario.id(), filtro.aba(), filtro.pagina())));
    }

    @PostMapping("/{candidaturaId}/desistir")
    public ResponseEntity<Void> desistir(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID candidaturaId) {
        desistirCandidaturaUseCase.executar(candidaturaId, usuario.id());
        return ResponseEntity.ok().build();
    }
}
