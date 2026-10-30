package com.limio.api.modulos.anuncio.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.usecase.CancelarAnuncioUseCase;

/** Cancelar anúncio antes da escolha (ETI-63). */
@RestController
@RequestMapping("/anuncios/{anuncioId}/cancelar")
public class CancelarAnuncioController {

    private final CancelarAnuncioUseCase cancelarAnuncioUseCase;

    public CancelarAnuncioController(CancelarAnuncioUseCase cancelarAnuncioUseCase) {
        this.cancelarAnuncioUseCase = cancelarAnuncioUseCase;
    }

    @PostMapping
    public ResponseEntity<Void> cancelar(@PathVariable UUID anuncioId, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        cancelarAnuncioUseCase.executar(anuncioId, usuario.id());
        return ResponseEntity.ok().build();
    }
}
