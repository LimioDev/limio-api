package com.limio.api.modulos.financeiro.assinaturagold.cobranca;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link Cobranca} — CRUD genérico (herdado), sem finder próprio ainda. */
@Component
public class CobrancaService extends BaseService<Cobranca, UUID, CobrancaRepository> {

    public CobrancaService(CobrancaRepository repository) {
        super(repository);
    }
}
