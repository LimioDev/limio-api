package com.limio.api.modulos.perfil.records;

import java.time.Instant;

public record AvaliacaoRecenteResponse(String avaliadorNome, int nota, String comentario, Instant data) {
}
