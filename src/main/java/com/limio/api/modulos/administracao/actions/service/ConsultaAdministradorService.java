package com.limio.api.modulos.administracao.actions.service;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaAdministrador;
import com.limio.api.modulos.administracao.administrador.AdministradorService;

/** Implementa {@link ConsultaAdministrador} para o {@code LeitorTokenAcesso} (ETI-117). */
@Component
public class ConsultaAdministradorService implements ConsultaAdministrador {

    private final AdministradorService administradorService;

    public ConsultaAdministradorService(AdministradorService administradorService) {
        this.administradorService = administradorService;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existe(UUID administradorId) {
        return administradorService.existePorId(administradorId);
    }
}
