package com.limio.api.modulos.administracao.administrador;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link Administrador} — CRUD genérico (herdado). */
@Component
public class AdministradorService extends BaseService<Administrador, UUID, AdministradorRepository> {

    public AdministradorService(AdministradorRepository repository) {
        super(repository);
    }
}
