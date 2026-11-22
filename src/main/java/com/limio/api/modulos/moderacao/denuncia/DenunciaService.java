package com.limio.api.modulos.moderacao.denuncia;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.moderacao.enums.StatusResolucao;

/** Camada técnica de persistência de {@link Denuncia} — CRUD genérico (herdado) + finders da entity. */
@Component
public class DenunciaService extends BaseService<Denuncia, UUID, DenunciaRepository> {

    public DenunciaService(DenunciaRepository repository) {
        super(repository);
    }

    /** Grava já: o {@code numero} só existe depois do insert, e a resposta do registro precisa dele. */
    public Denuncia salvarAgora(Denuncia denuncia) {
        return repository.saveAndFlush(denuncia);
    }

    public long contarPorStatus(StatusResolucao status) {
        return repository.countByStatusResolucao(status);
    }
}
