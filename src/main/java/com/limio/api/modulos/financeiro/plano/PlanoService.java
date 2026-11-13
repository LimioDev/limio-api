package com.limio.api.modulos.financeiro.plano;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link Plano} — CRUD genérico (herdado) + finder da entity. */
@Component
public class PlanoService extends BaseService<Plano, UUID, PlanoRepository> {

    public PlanoService(PlanoRepository repository) {
        super(repository);
    }

    public Optional<Plano> buscarAtivoPorCodigo(String codigo) {
        return repository.findByCodigoAndAtivoTrue(codigo);
    }
}
