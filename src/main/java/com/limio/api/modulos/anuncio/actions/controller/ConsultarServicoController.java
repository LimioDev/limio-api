package com.limio.api.modulos.anuncio.actions.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.anuncio.actions.mapper.ServicoCombinadoMapper;
import com.limio.api.modulos.anuncio.actions.usecase.ConsultarServicoUseCase;
import com.limio.api.modulos.anuncio.records.ServicoCombinadoResponse;

/**
 * Consultar o serviço combinado, com os contatos liberados (ETI-86). Só o Empregador dono e o Prestador
 * escolhido acessam: qualquer outra pessoa recebe 404, igual a um id inexistente.
 */
@RestController
@RequestMapping("/anuncios/{anuncioId}/servico")
public class ConsultarServicoController {

    private final ConsultarServicoUseCase consultarServicoUseCase;
    private final ServicoCombinadoMapper servicoCombinadoMapper;

    public ConsultarServicoController(ConsultarServicoUseCase consultarServicoUseCase,
            ServicoCombinadoMapper servicoCombinadoMapper) {
        this.consultarServicoUseCase = consultarServicoUseCase;
        this.servicoCombinadoMapper = servicoCombinadoMapper;
    }

    @GetMapping
    public ResponseEntity<ServicoCombinadoResponse> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable UUID anuncioId) {
        return ResponseEntity.ok(
                servicoCombinadoMapper.toResponse(consultarServicoUseCase.executar(usuario.id(), anuncioId)));
    }
}
