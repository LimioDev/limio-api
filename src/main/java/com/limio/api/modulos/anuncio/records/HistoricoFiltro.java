package com.limio.api.modulos.anuncio.records;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

import com.limio.api.modulos.anuncio.enums.StatusHistorico;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Query de {@code GET /anuncios/historico} (ETI-71). Tudo é opcional: sem {@code status} entram os três; sem datas, não
 * há limite de período. {@code dataInicio} e {@code dataFim} são dias ({@code yyyy-MM-dd}) de Brasília, os dois
 * inclusos, e {@code dataInicio} não pode vir depois de {@code dataFim}. {@code pagina} começa em 0; {@code tamanho}
 * fora de 1 a {@value #TAMANHO_MAXIMO} responde 400 {@code VALIDACAO_FALHOU}, como qualquer outro campo inválido.
 */
public record HistoricoFiltro(
        StatusHistorico status,
        @DateTimeFormat(iso = ISO.DATE) LocalDate dataInicio,
        @DateTimeFormat(iso = ISO.DATE) LocalDate dataFim,
        @PositiveOrZero(message = "{anuncio.historico.pagina}")
        Integer pagina,
        @Min(value = 1, message = "{anuncio.historico.tamanho}")
        @Max(value = HistoricoFiltro.TAMANHO_MAXIMO, message = "{anuncio.historico.tamanho}")
        Integer tamanho) {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 50;

    public HistoricoFiltro {
        pagina = pagina == null ? 0 : pagina;
        tamanho = tamanho == null ? TAMANHO_PADRAO : tamanho;
    }

    @AssertTrue(message = "{anuncio.historico.periodo}")
    public boolean isPeriodoValido() {
        return dataInicio == null || dataFim == null || !dataInicio.isAfter(dataFim);
    }
}
