package com.limio.api.modulos.anuncio;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CriterioDoMural(
        UUID prestadorId,
        Set<UUID> especialidadeIds,
        UUID categoriaOutrosId,
        double latitudeBase,
        double longitudeBase,
        int raioKm,
        Set<UUID> categoriasFiltro,
        Instant de,
        Instant ate) {

    public CriterioDoMural {
        especialidadeIds = Set.copyOf(especialidadeIds);
        categoriasFiltro = Set.copyOf(categoriasFiltro);
    }

    public boolean possuiOutros() {
        return especialidadeIds.contains(categoriaOutrosId);
    }
}
