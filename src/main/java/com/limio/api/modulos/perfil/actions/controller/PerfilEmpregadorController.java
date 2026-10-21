package com.limio.api.modulos.perfil.actions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.perfil.actions.mapper.PerfilEmpregadorMapper;
import com.limio.api.modulos.perfil.actions.usecase.ConsultarMeuPerfilUseCase;
import com.limio.api.modulos.perfil.actions.usecase.EditarNomeExibicaoUseCase;
import com.limio.api.modulos.perfil.records.EditarNomeExibicaoRequest;
import com.limio.api.modulos.perfil.records.PerfilEmpregadorResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/conta/perfil-empregador")
public class PerfilEmpregadorController {

    private final ConsultarMeuPerfilUseCase consultarMeuPerfilUseCase;
    private final EditarNomeExibicaoUseCase editarNomeExibicaoUseCase;
    private final PerfilEmpregadorMapper perfilEmpregadorMapper;

    public PerfilEmpregadorController(ConsultarMeuPerfilUseCase consultarMeuPerfilUseCase,
            EditarNomeExibicaoUseCase editarNomeExibicaoUseCase,
            PerfilEmpregadorMapper perfilEmpregadorMapper) {
        this.consultarMeuPerfilUseCase = consultarMeuPerfilUseCase;
        this.editarNomeExibicaoUseCase = editarNomeExibicaoUseCase;
        this.perfilEmpregadorMapper = perfilEmpregadorMapper;
    }

    @GetMapping
    public ResponseEntity<PerfilEmpregadorResponse> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(perfilEmpregadorMapper.toResponse(consultarMeuPerfilUseCase.executar(usuario.id())));
    }

    @PatchMapping("/nome-exibicao")
    public ResponseEntity<PerfilEmpregadorResponse> editarNomeExibicao(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody EditarNomeExibicaoRequest request) {
        return ResponseEntity.ok(perfilEmpregadorMapper.toResponse(
                editarNomeExibicaoUseCase.executar(usuario.id(), request.nomeExibicao())));
    }
}
