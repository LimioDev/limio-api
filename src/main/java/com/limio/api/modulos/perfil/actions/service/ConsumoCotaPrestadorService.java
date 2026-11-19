package com.limio.api.modulos.perfil.actions.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsumoCotaPrestador;
import com.limio.api.modulos.perfil.actions.usecase.ConsumirCotaDoMesUseCase;
import com.limio.api.modulos.perfil.actions.usecase.EstornarCotaDoMesUseCase;

@Component
public class ConsumoCotaPrestadorService implements ConsumoCotaPrestador {

    private final ConsumirCotaDoMesUseCase consumirCotaDoMesUseCase;

    private final EstornarCotaDoMesUseCase estornarCotaDoMesUseCase;

    public ConsumoCotaPrestadorService(ConsumirCotaDoMesUseCase consumirCotaDoMesUseCase,
            EstornarCotaDoMesUseCase estornarCotaDoMesUseCase) {
        this.consumirCotaDoMesUseCase = consumirCotaDoMesUseCase;
        this.estornarCotaDoMesUseCase = estornarCotaDoMesUseCase;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean estornarEscolha(UUID prestadorId, Instant escolhidaEm) {
        return estornarCotaDoMesUseCase.executar(prestadorId, escolhidaEm);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean consumirEscolha(UUID prestadorId, Instant ocorridaEm) {
        return consumirCotaDoMesUseCase.executar(prestadorId, ocorridaEm);
    }
}
