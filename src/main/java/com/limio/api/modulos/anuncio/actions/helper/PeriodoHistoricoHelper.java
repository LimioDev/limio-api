package com.limio.api.modulos.anuncio.actions.helper;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Traduz o período do histórico (ETI-71), dito em dias de Brasília, para o intervalo de instantes que a consulta usa.
 * Os dois dias entram: {@code dataInicio} a partir das 00:00 e {@code dataFim} até o fim do dia, ou seja, o limite
 * superior é a 00:00 do dia seguinte, exclusivo. Lado ausente = sem limite ({@code null}).
 */
public final class PeriodoHistoricoHelper {

    private PeriodoHistoricoHelper() {
    }

    public record Periodo(Instant de, Instant ate) {
    }

    public static Periodo de(LocalDate dataInicio, LocalDate dataFim) {
        Instant de = dataInicio == null ? null : inicioDoDia(dataInicio);
        Instant ate = dataFim == null ? null : inicioDoDia(dataFim.plusDays(1));
        return new Periodo(de, ate);
    }

    private static Instant inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay(JanelaDoMuralHelper.FUSO).toInstant();
    }
}
