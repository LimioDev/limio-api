package com.limio.api.modulos.anuncio.reagendamento;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.modulos.anuncio.enums.StatusReagendamento;

/** Camada técnica de persistência de {@link Reagendamento} — CRUD genérico (herdado) + finder da entity. */
@Component
public class ReagendamentoService extends BaseService<Reagendamento, UUID, ReagendamentoRepository> {

    public ReagendamentoService(ReagendamentoRepository repository) {
        super(repository);
    }

    public List<Reagendamento> buscarPendentesDosAnuncios(List<UUID> anuncioIds) {
        if (anuncioIds.isEmpty()) {
            return List.of();
        }
        return repository.findByAnuncioIdInAndStatus(anuncioIds, StatusReagendamento.PENDENTE);
    }
}
