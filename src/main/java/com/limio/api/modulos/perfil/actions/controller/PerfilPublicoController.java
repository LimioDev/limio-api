package com.limio.api.modulos.perfil.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.perfil.actions.mapper.PerfilPublicoMapper;
import com.limio.api.modulos.perfil.actions.usecase.ConsultarPerfilPublicoUseCase;
import com.limio.api.modulos.perfil.records.PerfilPublicoResponse;

/**
 * Perfil público e reputação de outra pessoa, sem dados de contato (ETI-105). Qualquer usuário logado pode
 * consultar — a exigência de login é a regra padrão de {@code SecurityConfig} ({@code anyRequest().authenticated()}),
 * sem checagem própria aqui. Inclusive o próprio perfil: o {@code usuarioId} vem da rota, não do token.
 */
@RestController
@RequestMapping("/perfis-publicos")
public class PerfilPublicoController {

    private final ConsultarPerfilPublicoUseCase consultarPerfilPublicoUseCase;
    private final PerfilPublicoMapper perfilPublicoMapper;

    public PerfilPublicoController(ConsultarPerfilPublicoUseCase consultarPerfilPublicoUseCase,
            PerfilPublicoMapper perfilPublicoMapper) {
        this.consultarPerfilPublicoUseCase = consultarPerfilPublicoUseCase;
        this.perfilPublicoMapper = perfilPublicoMapper;
    }

    @GetMapping("/{usuarioId}")
    public ResponseEntity<PerfilPublicoResponse> consultar(@PathVariable UUID usuarioId,
            @RequestParam PapelUsuario papel) {
        return ResponseEntity.ok(
                perfilPublicoMapper.toResponse(consultarPerfilPublicoUseCase.executar(usuarioId, papel)));
    }
}
