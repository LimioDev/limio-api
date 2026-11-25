package com.limio.api.modulos.anuncio.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.ReagendamentoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.ProporReagendamentoUseCase;
import com.limio.api.modulos.anuncio.records.ProporReagendamentoRequest;
import com.limio.api.modulos.anuncio.records.ReagendamentoResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/anuncios/{anuncioId}/reagendamentos")
public class ProporReagendamentoController {

    private final ProporReagendamentoUseCase proporReagendamentoUseCase;
    private final ReagendamentoMapper reagendamentoMapper;

    public ProporReagendamentoController(ProporReagendamentoUseCase proporReagendamentoUseCase,
            ReagendamentoMapper reagendamentoMapper) {
        this.proporReagendamentoUseCase = proporReagendamentoUseCase;
        this.reagendamentoMapper = reagendamentoMapper;
    }

    @PostMapping
    public ResponseEntity<ReagendamentoResponse> propor(@PathVariable UUID anuncioId,
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody ProporReagendamentoRequest request,
            HttpServletRequest http) {
        var proposta = proporReagendamentoUseCase.executar(usuario.id(), anuncioId, request.novaDataHora(),
                request.justificativa(), http.getRemoteAddr());
        return ResponseEntity.ok(reagendamentoMapper.toResponse(proposta));
    }
}
