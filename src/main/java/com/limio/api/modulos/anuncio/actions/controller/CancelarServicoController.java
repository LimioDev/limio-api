package com.limio.api.modulos.anuncio.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.ServicoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.CancelarServicoCombinadoUseCase;
import com.limio.api.modulos.anuncio.records.CancelamentoServicoResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/anuncios/{anuncioId}/servico/cancelar")
public class CancelarServicoController {

    private final CancelarServicoCombinadoUseCase cancelarServicoCombinadoUseCase;
    private final ServicoMapper servicoMapper;

    public CancelarServicoController(CancelarServicoCombinadoUseCase cancelarServicoCombinadoUseCase,
            ServicoMapper servicoMapper) {
        this.cancelarServicoCombinadoUseCase = cancelarServicoCombinadoUseCase;
        this.servicoMapper = servicoMapper;
    }

    @PostMapping
    public ResponseEntity<CancelamentoServicoResponse> cancelar(@PathVariable UUID anuncioId,
            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest http) {
        var cancelamento = cancelarServicoCombinadoUseCase.executar(usuario.id(), anuncioId, http.getRemoteAddr());
        return ResponseEntity.ok(servicoMapper.toCancelamentoResponse(cancelamento));
    }
}
