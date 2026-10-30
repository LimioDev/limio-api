package com.limio.api.modulos.perfil.records;

import java.time.OffsetDateTime;
import java.util.List;

import com.limio.api.comum.portas.enums.Plano;

/**
 * Cota do mês do Prestador (US24). {@code anoMes} é {@code AAAA-MM}; {@code tetoAplicado} é nulo no GOLD (sem teto);
 * {@code renovaEm} é o dia 1 do mês seguinte, 00:00 de America/Sao_Paulo, com o offset de lá.
 */
public record CotaDoMesResponse(Plano plano, String anoMes, int escolhasConsumidas, Integer tetoAplicado,
        boolean esgotada, OffsetDateTime renovaEm, List<EscolhaDoMesResponse> escolhas) {
}
