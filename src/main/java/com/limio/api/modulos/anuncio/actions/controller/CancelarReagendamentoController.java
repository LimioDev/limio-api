package com.limio.api.modulos.anuncio.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.ReagendamentoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.CancelarReagendamentoUseCase;
import com.limio.api.modulos.anuncio.records.ReagendamentoResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/reagendamentos/{reagendamentoId}/cancelar")
public class CancelarReagendamentoController {

    private final CancelarReagendamentoUseCase cancelarReagendamentoUseCase;
    private final ReagendamentoMapper reagendamentoMapper;

    public CancelarReagendamentoController(CancelarReagendamentoUseCase cancelarReagendamentoUseCase,
            ReagendamentoMapper reagendamentoMapper) {
        this.cancelarReagendamentoUseCase = cancelarReagendamentoUseCase;
        this.reagendamentoMapper = reagendamentoMapper;
    }

    @PostMapping
    public ResponseEntity<ReagendamentoResponse> cancelar(@PathVariable UUID reagendamentoId,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest http) {
        var cancelada = cancelarReagendamentoUseCase.executar(usuario.id(), reagendamentoId, http.getRemoteAddr());
        return ResponseEntity.ok(reagendamentoMapper.toResponse(cancelada));
    }
}
