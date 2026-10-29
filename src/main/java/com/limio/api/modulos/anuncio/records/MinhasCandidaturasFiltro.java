package com.limio.api.modulos.anuncio.records;

import com.limio.api.modulos.anuncio.enums.AbaCandidatura;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Query string de {@code GET /candidaturas/minhas} (ETI-77). {@code pagina} começa em 0 e é opcional. */
public record MinhasCandidaturasFiltro(@NotNull AbaCandidatura aba, @PositiveOrZero Integer pagina) {

    public MinhasCandidaturasFiltro {
        if (pagina == null) {
            pagina = 0;
        }
    }
}
