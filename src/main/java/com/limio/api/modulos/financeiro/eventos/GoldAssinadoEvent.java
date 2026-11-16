package com.limio.api.modulos.financeiro.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado pelo {@code AssinarGoldUseCase} depois de aprovar a cobrança (ETI-81, UC "enviar o recibo").
 * Interno a {@code financeiro}: só {@code ReciboEventosService} escuta, não sobe pra {@code comum/}
 * (ADR-0003 — evento entre módulos só nasce quando outro módulo precisa reagir).
 */
public record GoldAssinadoEvent(UUID usuarioId, String nomePlano, long valorCentavos, String moeda,
        Instant renovaEm, String numeroRecibo) {
}
