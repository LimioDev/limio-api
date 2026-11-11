package com.limio.api.modulos.financeiro.actions.service.asaas;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link ClienteAsaas} — CRUD genérico (herdado) + finder da entity. */
@Component
public class ClienteAsaasService extends BaseService<ClienteAsaas, UUID, ClienteAsaasRepository> {

    public ClienteAsaasService(ClienteAsaasRepository repository) {
        super(repository);
    }

    public Optional<ClienteAsaas> buscarPorUsuario(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }
}
