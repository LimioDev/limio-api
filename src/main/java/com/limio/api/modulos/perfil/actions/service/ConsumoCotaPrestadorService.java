package com.limio.api.modulos.perfil.actions.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsumoCotaPrestador;
import com.limio.api.modulos.perfil.actions.usecase.ConsumirCotaDoMesUseCase;

@Component
public class ConsumoCotaPrestadorService implements ConsumoCotaPrestador {

    private final ConsumirCotaDoMesUseCase consumirCotaDoMesUseCase;

    public ConsumoCotaPrestadorService(ConsumirCotaDoMesUseCase consumirCotaDoMesUseCase) {
        this.consumirCotaDoMesUseCase = consumirCotaDoMesUseCase;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean consumirEscolha(UUID prestadorId, Instant ocorridaEm) {
        return consumirCotaDoMesUseCase.executar(prestadorId, ocorridaEm);
    }
}
