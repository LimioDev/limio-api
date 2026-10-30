package com.limio.api.modulos.perfil.actions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.comum.seguranca.UsuarioAutenticado;
import com.limio.api.modulos.perfil.actions.mapper.CotaDoMesMapper;
import com.limio.api.modulos.perfil.actions.usecase.ConsultarCotaDoMesUseCase;
import com.limio.api.modulos.perfil.records.CotaDoMesResponse;

/**
 * Cota do mês do Prestador logado (ETI-79). O dono vem sempre do token ({@link UsuarioAutenticado}): ninguém consulta
 * a cota de outro.
 */
@RestController
@RequestMapping("/perfil/prestador/cota")
public class CotaDoMesController {

    private final ConsultarCotaDoMesUseCase consultarCotaDoMesUseCase;
    private final CotaDoMesMapper cotaDoMesMapper;

    public CotaDoMesController(ConsultarCotaDoMesUseCase consultarCotaDoMesUseCase, CotaDoMesMapper cotaDoMesMapper) {
        this.consultarCotaDoMesUseCase = consultarCotaDoMesUseCase;
        this.cotaDoMesMapper = cotaDoMesMapper;
    }

    @GetMapping
    public ResponseEntity<CotaDoMesResponse> consultar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(cotaDoMesMapper.toResponse(
                consultarCotaDoMesUseCase.executar(usuario.id(), usuario.papel())));
    }
}
