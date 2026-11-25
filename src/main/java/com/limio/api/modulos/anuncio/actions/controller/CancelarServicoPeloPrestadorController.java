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
import com.limio.api.modulos.anuncio.actions.usecase.CancelarServicoPeloPrestadorUseCase;
import com.limio.api.modulos.anuncio.records.CancelamentoPeloPrestadorResponse;

@RestController
@RequestMapping("/anuncios/{anuncioId}/servico/cancelar-prestador")
public class CancelarServicoPeloPrestadorController {

    private final CancelarServicoPeloPrestadorUseCase cancelarServicoPeloPrestadorUseCase;
    private final ServicoMapper servicoMapper;

    public CancelarServicoPeloPrestadorController(CancelarServicoPeloPrestadorUseCase cancelarServicoPeloPrestadorUseCase,
            ServicoMapper servicoMapper) {
        this.cancelarServicoPeloPrestadorUseCase = cancelarServicoPeloPrestadorUseCase;
        this.servicoMapper = servicoMapper;
    }

    @PostMapping
    public ResponseEntity<CancelamentoPeloPrestadorResponse> cancelar(@PathVariable UUID anuncioId,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        var anuncio = cancelarServicoPeloPrestadorUseCase.executar(usuario.id(), anuncioId);
        return ResponseEntity.ok(servicoMapper.toCancelamentoPeloPrestadorResponse(anuncio));
    }
}
