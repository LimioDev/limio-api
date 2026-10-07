package com.limio.api.modulos.auth.cpfbloqueado;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.auth.actions.helper.CpfHasher;

/** Camada técnica de persistência de {@link CpfBloqueado} — CRUD genérico (herdado) + finder da entity. */
@Component
public class CpfBloqueadoService extends BaseService<CpfBloqueado, UUID, CpfBloqueadoRepository> {

    private final CpfHasher cpfHasher;

    public CpfBloqueadoService(CpfBloqueadoRepository repository, CpfHasher cpfHasher) {
        super(repository);
        this.cpfHasher = cpfHasher;
    }

    public boolean estaBloqueado(String cpf, Instant limiteCooldown) {
        return repository.existsByCpfHmacAndBloqueadoEmAfterAndLiberadoEmIsNull(cpfHasher.hash(cpf), limiteCooldown);
    }
}
