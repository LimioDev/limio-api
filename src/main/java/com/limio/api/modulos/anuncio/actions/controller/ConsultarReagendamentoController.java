package com.limio.api.modulos.anuncio.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.ReagendamentoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarReagendamentoUseCase;
import com.limio.api.modulos.anuncio.records.ReagendamentoResponse;

@RestController
@RequestMapping("/reagendamentos/{reagendamentoId}")
public class ConsultarReagendamentoController {

    private final ConsultarReagendamentoUseCase consultarReagendamentoUseCase;
    private final ReagendamentoMapper reagendamentoMapper;

    public ConsultarReagendamentoController(ConsultarReagendamentoUseCase consultarReagendamentoUseCase,
            ReagendamentoMapper reagendamentoMapper) {
        this.consultarReagendamentoUseCase = consultarReagendamentoUseCase;
        this.reagendamentoMapper = reagendamentoMapper;
    }

    @GetMapping
    public ResponseEntity<ReagendamentoResponse> consultar(@PathVariable UUID reagendamentoId,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        var consultado = consultarReagendamentoUseCase.executar(usuario.id(), reagendamentoId);
        return ResponseEntity.ok(reagendamentoMapper.toResponse(consultado));
    }
}
