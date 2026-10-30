package com.limio.api.modulos.perfil.perfilprestador.cotamensal;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;

/** Camada técnica de persistência de {@link CotaMensal} — CRUD genérico (herdado) + finder da entity. */
@Component
public class CotaMensalService extends BaseService<CotaMensal, UUID, CotaMensalRepository> {

    public CotaMensalService(CotaMensalRepository repository) {
        super(repository);
    }

    /** Vazio é fluxo normal: nenhuma escolha consumida no mês ainda. */
    public Optional<CotaMensal> buscarDoMes(UUID perfilPrestadorId, YearMonth anoMes) {
        return repository.findByPerfilPrestadorIdAndAnoMes(perfilPrestadorId, anoMes);
    }
}
