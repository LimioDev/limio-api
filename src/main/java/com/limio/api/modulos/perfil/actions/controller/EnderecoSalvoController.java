package com.limio.api.modulos.perfil.actions.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.perfil.actions.mapper.EnderecoSalvoMapper;
import com.limio.api.modulos.perfil.actions.usecase.CriarEnderecoSalvoUseCase;
import com.limio.api.modulos.perfil.actions.usecase.EditarEnderecoSalvoUseCase;
import com.limio.api.modulos.perfil.actions.usecase.ListarEnderecosSalvosUseCase;
import com.limio.api.modulos.perfil.records.EnderecoSalvoRequest;
import com.limio.api.modulos.perfil.records.EnderecoSalvoResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/conta/perfil-empregador/enderecos")
public class EnderecoSalvoController {

    private final ListarEnderecosSalvosUseCase listarEnderecosSalvosUseCase;
    private final CriarEnderecoSalvoUseCase criarEnderecoSalvoUseCase;
    private final EditarEnderecoSalvoUseCase editarEnderecoSalvoUseCase;
    private final EnderecoSalvoMapper enderecoSalvoMapper;

    public EnderecoSalvoController(ListarEnderecosSalvosUseCase listarEnderecosSalvosUseCase,
            CriarEnderecoSalvoUseCase criarEnderecoSalvoUseCase,
            EditarEnderecoSalvoUseCase editarEnderecoSalvoUseCase,
            EnderecoSalvoMapper enderecoSalvoMapper) {
        this.listarEnderecosSalvosUseCase = listarEnderecosSalvosUseCase;
        this.criarEnderecoSalvoUseCase = criarEnderecoSalvoUseCase;
        this.editarEnderecoSalvoUseCase = editarEnderecoSalvoUseCase;
        this.enderecoSalvoMapper = enderecoSalvoMapper;
    }

    @GetMapping
    public ResponseEntity<List<EnderecoSalvoResponse>> listarEndereco(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(enderecoSalvoMapper.toResponse(listarEnderecosSalvosUseCase.executar(usuario.id())));
    }

    @PostMapping
    public ResponseEntity<EnderecoSalvoResponse> criarEndereco(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody EnderecoSalvoRequest request) {
        var criado = criarEnderecoSalvoUseCase.executar(usuario.id(), enderecoSalvoMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(enderecoSalvoMapper.toResponse(criado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnderecoSalvoResponse> editarEndereco(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID id, @Valid @RequestBody EnderecoSalvoRequest request) {
        var editado = editarEnderecoSalvoUseCase.executar(usuario.id(), id, enderecoSalvoMapper.toEntity(request));
        return ResponseEntity.ok(enderecoSalvoMapper.toResponse(editado));
    }
}
