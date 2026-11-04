package com.limio.api.modulos.perfil.actions.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.portas.ConsultaPerfilPrestador;
import com.limio.api.comum.portas.ResumoPerfilPrestador;
import com.limio.api.modulos.perfil.categoria.Categoria;
import com.limio.api.modulos.perfil.categoria.CategoriaService;
import com.limio.api.modulos.perfil.geo.Coordenadas;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestadorService;

@Component
public class ConsultaPerfilPrestadorService implements ConsultaPerfilPrestador {

    private final PerfilPrestadorService perfilPrestadorService;
    private final CategoriaService categoriaService;

    public ConsultaPerfilPrestadorService(PerfilPrestadorService perfilPrestadorService,
            CategoriaService categoriaService) {
        this.perfilPrestadorService = perfilPrestadorService;
        this.categoriaService = categoriaService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResumoPerfilPrestador> buscarCompleto(UUID usuarioId) {
        return perfilPrestadorService.buscarPorUsuario(usuarioId)
                .filter(PerfilPrestador::isCompleto)
                .filter(perfil -> perfil.getCoordenadasBase() != null && perfil.getRaioAtendimentoKm() != null)
                .map(this::resumoDe);
    }

    private ResumoPerfilPrestador resumoDe(PerfilPrestador perfil) {
        Set<UUID> especialidades = perfil.getEspecialidades().stream()
                .map(Categoria::getId)
                .collect(Collectors.toSet());
        UUID outrosId = categoriaService.buscarOutros()
                .map(Categoria::getId)
                .orElseThrow(() -> new IllegalStateException("categoria protegida \"Outros\" ausente da base"));
        Coordenadas base = perfil.getCoordenadasBase();
        return new ResumoPerfilPrestador(especialidades, outrosId, perfil.getRaioAtendimentoKm(),
                base.latitude(), base.longitude());
    }
}
