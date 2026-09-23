package com.limio.api.modulos.auth.actions.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.modulos.auth.Usuario;
import com.limio.api.modulos.auth.actions.mapper.UsuarioMapper;
import com.limio.api.modulos.auth.actions.usecase.CadastrarUsuarioUseCase;
import com.limio.api.modulos.auth.records.CadastroRequest;
import com.limio.api.modulos.auth.records.UsuarioResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final CadastrarUsuarioUseCase cadastrarUsuarioUseCase;
    private final UsuarioMapper usuarioMapper;

    public AuthController(CadastrarUsuarioUseCase cadastrarUsuarioUseCase, UsuarioMapper usuarioMapper) {
        this.cadastrarUsuarioUseCase = cadastrarUsuarioUseCase;
        this.usuarioMapper = usuarioMapper;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<UsuarioResponse> cadastrar(@Valid @RequestBody CadastroRequest request) {
        Usuario usuarioCandidato = usuarioMapper.toEntity(request);
        Usuario usuarioCriado = cadastrarUsuarioUseCase.executar(usuarioCandidato, request.senha());
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioMapper.toResponse(usuarioCriado));
    }
}
