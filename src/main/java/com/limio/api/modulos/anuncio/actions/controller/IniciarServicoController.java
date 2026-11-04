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
import com.limio.api.modulos.anuncio.actions.usecase.IniciarServicoUseCase;
import com.limio.api.modulos.anuncio.records.ServicoIniciadoResponse;

/** Iniciar o serviço (ETI-88). */
@RestController
@RequestMapping("/anuncios/{anuncioId}/iniciar")
public class IniciarServicoController {

    private final IniciarServicoUseCase iniciarServicoUseCase;
    private final ServicoMapper servicoMapper;

    public IniciarServicoController(IniciarServicoUseCase iniciarServicoUseCase, ServicoMapper servicoMapper) {
        this.iniciarServicoUseCase = iniciarServicoUseCase;
        this.servicoMapper = servicoMapper;
    }

    @PostMapping
    public ResponseEntity<ServicoIniciadoResponse> iniciar(@PathVariable UUID anuncioId,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        var anuncio = iniciarServicoUseCase.executar(anuncioId, usuario.id());
        return ResponseEntity.ok(servicoMapper.toIniciadoResponse(anuncio));
    }
}
