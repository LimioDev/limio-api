package com.limio.api.modulos.perfil.perfilprestador.cotamensal;

import java.time.YearMonth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link YearMonth} ↔ {@code 'AAAA-MM'} (coluna {@code cota_mensal.ano_mes}). */
@Converter
public class AnoMesConverter implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth anoMes) {
        return anoMes == null ? null : anoMes.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String coluna) {
        return coluna == null ? null : YearMonth.parse(coluna);
    }
}
