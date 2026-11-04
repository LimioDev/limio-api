package com.limio.api.modulos.anuncio.actions.helper;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import com.limio.api.modulos.anuncio.enums.QuandoMural;

/**
 * Faixa de datas do filtro "Quando". A semana vai de segunda a domingo no fuso de Brasília (a data do
 * serviço é dita pelo usuário no horário local). Sempre começa em {@code agora}: serviço no passado não
 * aparece. {@code ate} nulo = sem limite.
 */
public final class JanelaDoMuralHelper {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private JanelaDoMuralHelper() {
    }

    public record Janela(Instant de, Instant ate) {
    }

    public static Janela de(QuandoMural quando, Instant agora) {
        LocalDate hoje = agora.atZone(FUSO).toLocalDate();
        return switch (quando) {
            case QUALQUER -> new Janela(agora, null);
            // até a próxima segunda 00:00 (exclusive): hoje + o que falta da semana
            case ESTA_SEMANA -> new Janela(agora, inicioDoDia(hoje.with(TemporalAdjusters.next(DayOfWeek.MONDAY))));
            case FIM_DE_SEMANA -> {
                // domingo ainda é "este" fim de semana: o sábado dele foi ontem
                LocalDate sabado = hoje.getDayOfWeek() == DayOfWeek.SUNDAY
                        ? hoje.minusDays(1)
                        : hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
                Instant inicio = inicioDoDia(sabado);
                yield new Janela(inicio.isAfter(agora) ? inicio : agora, inicioDoDia(sabado.plusDays(2)));
            }
        };
    }

    private static Instant inicioDoDia(LocalDate dia) {
        return dia.atStartOfDay(FUSO).toInstant();
    }
}
