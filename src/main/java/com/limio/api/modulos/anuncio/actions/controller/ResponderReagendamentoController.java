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
import com.limio.api.modulos.anuncio.actions.mapper.ServicoCombinadoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarServicoUseCase;
import com.limio.api.modulos.anuncio.actions.usecase.ResponderReagendamentoUseCase;
import com.limio.api.modulos.anuncio.records.ResponderReagendamentoRequest;
import com.limio.api.modulos.anuncio.records.ServicoCombinadoResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/reagendamentos/{reagendamentoId}/responder")
public class ResponderReagendamentoController {

    private final ResponderReagendamentoUseCase responderReagendamentoUseCase;
    private final ConsultarServicoUseCase consultarServicoUseCase;
    private final ServicoCombinadoMapper servicoCombinadoMapper;

    public ResponderReagendamentoController(ResponderReagendamentoUseCase responderReagendamentoUseCase,
            ConsultarServicoUseCase consultarServicoUseCase, ServicoCombinadoMapper servicoCombinadoMapper) {
        this.responderReagendamentoUseCase = responderReagendamentoUseCase;
        this.consultarServicoUseCase = consultarServicoUseCase;
        this.servicoCombinadoMapper = servicoCombinadoMapper;
    }

    @PostMapping
    public ResponseEntity<ServicoCombinadoResponse> responder(@PathVariable UUID reagendamentoId,
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody ResponderReagendamentoRequest request,
            HttpServletRequest http) {
        var respondida = responderReagendamentoUseCase.executar(usuario.id(), reagendamentoId, request.decisao(),
                http.getRemoteAddr());
        return ResponseEntity.ok(servicoCombinadoMapper
                .toResponse(consultarServicoUseCase.executar(usuario.id(), respondida.getAnuncioId())));
    }
}
