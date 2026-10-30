package com.limio.api.modulos.perfil.actions.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaEspecialidades;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

@Component
public class ConsultaEspecialidadesService implements ConsultaEspecialidades {

    private final PerfilPrestadorService perfilPrestadorService;

    public ConsultaEspecialidadesService(PerfilPrestadorService perfilPrestadorService) {
        this.perfilPrestadorService = perfilPrestadorService;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> listarNomes(Collection<UUID> prestadorIds) {
        return perfilPrestadorService.buscarPorUsuarios(prestadorIds).stream()
                .collect(Collectors.toMap(PerfilPrestador::getUsuarioId, ConsultaEspecialidadesService::nomes));
    }

    private static List<String> nomes(PerfilPrestador perfil) {
        return perfil.getEspecialidades().stream()
                .sorted(Categoria.ORDEM_EXIBICAO)
                .map(Categoria::getNome)
                .toList();
    }
}
