package com.limio.api.modulos.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link CpfBloqueado} — CRUD genérico (herdado) + finder da entity. */
@Component
public class CpfBloqueadoService extends BaseService<CpfBloqueado, UUID, CpfBloqueadoRepository> {

    public CpfBloqueadoService(CpfBloqueadoRepository repository) {
        super(repository);
    }

    public boolean estaBloqueado(String cpf, Instant limiteCooldown) {
        return repository.existsByCpfAndCriadoEmAfter(cpf, limiteCooldown);
    }
}
