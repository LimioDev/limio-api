package com.limio.api.comum.portas;

import java.util.Set;
import java.util.UUID;

public record ResumoPerfilPrestador(Set<UUID> especialidadeIds, UUID categoriaOutrosId, int raioAtendimentoKm,
        double latitudeBase, double longitudeBase) {

    public ResumoPerfilPrestador {
        especialidadeIds = Set.copyOf(especialidadeIds);
    }

    public boolean possuiOutros() {
        return especialidadeIds.contains(categoriaOutrosId);
    }
}
