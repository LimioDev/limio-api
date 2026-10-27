package com.limio.api.modulos.anuncio.actions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.PainelEmpregadorMapper;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarPainelEmpregadorUseCase;
import com.limio.api.modulos.anuncio.records.PainelEmpregadorResponse;

/** Tela "Início" do Empregador (TICKET-0052). */
@RestController
@RequestMapping("/anuncios/painel-empregador")
public class PainelEmpregadorController {

    private final ConsultarPainelEmpregadorUseCase consultarPainelEmpregadorUseCase;
    private final PainelEmpregadorMapper painelEmpregadorMapper;

    public PainelEmpregadorController(ConsultarPainelEmpregadorUseCase consultarPainelEmpregadorUseCase,
            PainelEmpregadorMapper painelEmpregadorMapper) {
        this.consultarPainelEmpregadorUseCase = consultarPainelEmpregadorUseCase;
        this.painelEmpregadorMapper = painelEmpregadorMapper;
    }

    @GetMapping
    public ResponseEntity<PainelEmpregadorResponse> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(painelEmpregadorMapper.toResponse(
                consultarPainelEmpregadorUseCase.executar(usuario.id())));
    }
}
