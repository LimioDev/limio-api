package com.limio.api.modulos.administracao.actions.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.modulos.administracao.actions.mapper.DashboardMapper;
import com.limio.api.modulos.administracao.actions.usecase.ConsultarDashboardUseCase;
import com.limio.api.modulos.administracao.records.DashboardResponse;

/**
 * Dashboard do administrador (ETI-117). Sem corpo e sem parâmetros. Fica em {@code /admin/**}, que o
 * {@code SecurityConfig} libera só para o administrador: usuário do app recebe 403 {@code ACESSO_NEGADO}, e sem token,
 * 401 {@code NAO_AUTENTICADO}.
 */
@RestController
public class DashboardController {

    private final ConsultarDashboardUseCase consultarDashboardUseCase;
    private final DashboardMapper dashboardMapper;

    public DashboardController(ConsultarDashboardUseCase consultarDashboardUseCase, DashboardMapper dashboardMapper) {
        this.consultarDashboardUseCase = consultarDashboardUseCase;
        this.dashboardMapper = dashboardMapper;
    }

    @GetMapping("/admin/dashboard")
    public DashboardResponse consultar() {
        return dashboardMapper.toResponse(consultarDashboardUseCase.executar());
    }
}
