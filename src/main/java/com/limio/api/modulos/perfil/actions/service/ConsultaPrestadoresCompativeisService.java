package com.limio.api.modulos.perfil.actions.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ConsultaPrestadoresCompativeis;
import com.limio.api.comum.portas.PrestadorCompativel;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

/** Implementa {@link ConsultaPrestadoresCompativeis} (ETI-65). A regra fica na consulta do repository. */
@Component
public class ConsultaPrestadoresCompativeisService implements ConsultaPrestadoresCompativeis {

    private final PerfilPrestadorService perfilPrestadorService;

    public ConsultaPrestadoresCompativeisService(PerfilPrestadorService perfilPrestadorService) {
        this.perfilPrestadorService = perfilPrestadorService;
    }

    /**
     * A distância nunca vem nula: a consulta só traz perfil completo, que tem coordenadas. O {@code requireNonNull}
     * deixa isso explícito em vez de um NPE no desembrulho para {@code double}.
     */
    @Override
    public List<PrestadorCompativel> buscarCompativeis(UUID categoriaId, double latitude, double longitude) {
        return perfilPrestadorService.buscarCompativeis(categoriaId, latitude, longitude).stream()
                .map(linha -> new PrestadorCompativel(linha.getUsuarioId(), Objects.requireNonNull(
                        linha.getDistanciaKm(), "perfil completo sem distância: " + linha.getUsuarioId())))
                .toList();
    }
}
