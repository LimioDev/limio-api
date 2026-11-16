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
import com.limio.api.modulos.anuncio.actions.mapper.ServicoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.ValidarServicoUseCase;
import com.limio.api.modulos.anuncio.records.ServicoValidadoResponse;
import com.limio.api.modulos.anuncio.records.ValidarServicoRequest;

import jakarta.validation.Valid;

/** Validar o serviço, presencial ou pela foto (ETI-92). */
@RestController
@RequestMapping("/anuncios/{anuncioId}/validar")
public class ValidarServicoController {

    private final ValidarServicoUseCase validarServicoUseCase;
    private final ServicoMapper servicoMapper;

    public ValidarServicoController(ValidarServicoUseCase validarServicoUseCase, ServicoMapper servicoMapper) {
        this.validarServicoUseCase = validarServicoUseCase;
        this.servicoMapper = servicoMapper;
    }

    @PostMapping
    public ResponseEntity<ServicoValidadoResponse> validar(@PathVariable UUID anuncioId,
            @AuthenticationPrincipal UsuarioAutenticado usuario, @Valid @RequestBody ValidarServicoRequest request) {
        var anuncio = validarServicoUseCase.executar(anuncioId, usuario.id(), request.modo());
        return ResponseEntity.ok(servicoMapper.toValidadoResponse(anuncio));
    }
}
