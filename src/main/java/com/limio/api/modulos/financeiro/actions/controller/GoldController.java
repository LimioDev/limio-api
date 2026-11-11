package com.limio.api.modulos.financeiro.actions.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.financeiro.actions.mapper.GoldMapper;
import com.limio.api.modulos.financeiro.actions.usecase.AssinarGoldUseCase;
import com.limio.api.modulos.financeiro.actions.usecase.ConsultarGoldUseCase;
import com.limio.api.modulos.financeiro.records.AssinarGoldRequest;
import com.limio.api.modulos.financeiro.records.AssinarGoldResponse;
import com.limio.api.modulos.financeiro.records.ConsultarGoldResponse;

import jakarta.validation.Valid;

/** Consultar e assinar o plano GOLD (ETI-81). A tela "Assinatura" fica no card par [FRONT-END]. */
@RestController
@RequestMapping("/financeiro/gold")
public class GoldController {

    private final ConsultarGoldUseCase consultarGoldUseCase;
    private final AssinarGoldUseCase assinarGoldUseCase;
    private final GoldMapper goldMapper;

    public GoldController(ConsultarGoldUseCase consultarGoldUseCase, AssinarGoldUseCase assinarGoldUseCase,
            GoldMapper goldMapper) {
        this.consultarGoldUseCase = consultarGoldUseCase;
        this.assinarGoldUseCase = assinarGoldUseCase;
        this.goldMapper = goldMapper;
    }

    @GetMapping
    public ResponseEntity<ConsultarGoldResponse> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        var consulta = consultarGoldUseCase.executar(usuario.id(), usuario.papel());
        return ResponseEntity.ok(goldMapper.toResponse(consulta));
    }

    @PostMapping("/assinatura")
    public ResponseEntity<AssinarGoldResponse> assinar(@Valid @RequestBody AssinarGoldRequest request,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        var assinatura = assinarGoldUseCase.executar(usuario.id(), usuario.papel(), request.codigoPlano(),
                request.gatewayToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(goldMapper.toResponse(assinatura));
    }
}
