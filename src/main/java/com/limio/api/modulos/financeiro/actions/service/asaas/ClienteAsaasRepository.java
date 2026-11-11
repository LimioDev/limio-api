package com.limio.api.modulos.financeiro.actions.service.asaas;

import java.util.Optional;
import java.util.UUID;

import com.limio.api.comum.base.BaseRepository;

public interface ClienteAsaasRepository extends BaseRepository<ClienteAsaas, UUID> {

    Optional<ClienteAsaas> findByUsuarioId(UUID usuarioId);
}
