package com.limio.api.modulos.perfil.actions.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ConsultaAvaliacoes;
import com.limio.api.comum.portas.ResumoAvaliacoes;

@Component
public class ConsultaAvaliacoesService implements ConsultaAvaliacoes {

    @Override
    public Map<UUID, ResumoAvaliacoes> resumir(Collection<UUID> prestadorIds) {
        return Map.of();
    }
}
