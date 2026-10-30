package com.limio.api.modulos.perfil.records;

import java.time.Instant;
import java.util.UUID;

/** Um item de "Onde você usou sua cota": o serviço, quando ele acontece, quando o Prestador foi escolhido e o status. */
public record EscolhaDoMesResponse(UUID anuncioId, String titulo, Instant dataHoraServico, Instant escolhidaEm,
        String status) {
}
