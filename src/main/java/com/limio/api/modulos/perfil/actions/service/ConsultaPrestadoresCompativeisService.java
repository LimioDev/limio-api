package com.limio.api.modulos.perfil.actions.service;

import java.util.List;
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

    @Override
    public List<PrestadorCompativel> buscarCompativeis(UUID categoriaId, double latitude, double longitude) {
        return perfilPrestadorService.buscarCompativeis(categoriaId, latitude, longitude).stream()
                .map(linha -> new PrestadorCompativel(linha.getUsuarioId(), linha.getDistanciaKm()))
                .toList();
    }
}
