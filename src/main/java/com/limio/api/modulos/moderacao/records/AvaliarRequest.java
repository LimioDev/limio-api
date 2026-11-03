package com.limio.api.modulos.moderacao.records;

import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /anuncios/{anuncioId}/avaliacoes}. {@code nota} não tem {@code @NotNull}: sem nota o usecase
 * responde {@code COMENTARIO_SEM_NOTA} quando veio comentário, e esse código não sairia da validação do record.
 * Comentário em branco conta como ausente.
 */
public record AvaliarRequest(
        @Min(value = Avaliacao.NOTA_MINIMA, message = "{avaliacao.nota.faixa}")
        @Max(value = Avaliacao.NOTA_MAXIMA, message = "{avaliacao.nota.faixa}")
        Integer nota,
        @Size(max = Avaliacao.COMENTARIO_MAX, message = "{avaliacao.comentario.tamanho}")
        String comentario) {

    public AvaliarRequest {
        comentario = comentario == null || comentario.isBlank() ? null : comentario.strip();
    }
}
