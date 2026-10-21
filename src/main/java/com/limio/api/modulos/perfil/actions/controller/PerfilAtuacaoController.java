package com.limio.api.modulos.perfil.actions.controller;

import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.perfil.actions.mapper.PerfilAtuacaoMapper;
import com.limio.api.modulos.perfil.actions.usecase.ConsultarPerfilAtuacaoUseCase;
import com.limio.api.modulos.perfil.actions.usecase.SalvarPerfilAtuacaoUseCase;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.records.PerfilAtuacaoRequest;
import com.limio.api.modulos.perfil.records.PerfilAtuacaoResponse;

import jakarta.validation.Valid;

/**
 * Perfil de atuação do usuário logado (TICKET-0054). O dono vem sempre do token
 * ({@link UsuarioAutenticado}), nunca do corpo nem da rota — ninguém lê nem altera o perfil de outro.
 */
@RestController
@RequestMapping("/perfil/prestador/atuacao")
public class PerfilAtuacaoController {

    private final ConsultarPerfilAtuacaoUseCase consultarPerfilAtuacaoUseCase;
    private final SalvarPerfilAtuacaoUseCase salvarPerfilAtuacaoUseCase;
    private final PerfilAtuacaoMapper perfilAtuacaoMapper;

    public PerfilAtuacaoController(ConsultarPerfilAtuacaoUseCase consultarPerfilAtuacaoUseCase,
            SalvarPerfilAtuacaoUseCase salvarPerfilAtuacaoUseCase,
            PerfilAtuacaoMapper perfilAtuacaoMapper) {
        this.consultarPerfilAtuacaoUseCase = consultarPerfilAtuacaoUseCase;
        this.salvarPerfilAtuacaoUseCase = salvarPerfilAtuacaoUseCase;
        this.perfilAtuacaoMapper = perfilAtuacaoMapper;
    }

    @GetMapping
    public ResponseEntity<PerfilAtuacaoResponse> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        PerfilPrestador perfil = consultarPerfilAtuacaoUseCase.executar(usuario.id());
        return ResponseEntity.ok(perfilAtuacaoMapper.toResponse(perfil));
    }

    /** Id de especialidade repetido conta uma vez só. */
    @PutMapping
    public ResponseEntity<PerfilAtuacaoResponse> salvar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody PerfilAtuacaoRequest request) {
        PerfilPrestador salvo = salvarPerfilAtuacaoUseCase.executar(usuario.id(),
                Set.copyOf(request.especialidades()), request.raioAtendimentoKm(), request.municipioBase(),
                request.ufBase(), perfilAtuacaoMapper.toJanelas(request.janelas()));
        return ResponseEntity.ok(perfilAtuacaoMapper.toResponse(salvo));
    }
}
