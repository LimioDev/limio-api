package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EditarAnuncioRequest(
        @NotNull(message = "{anuncio.categoriaId.obrigatoria}")
        UUID categoriaId,

        @NotBlank(message = "{anuncio.titulo.obrigatorio}")
        @Size(min = 5, max = 80, message = "{anuncio.titulo.tamanho}")
        String titulo,

        @NotBlank(message = "{anuncio.descricao.obrigatoria}")
        @Size(max = 600, message = "{anuncio.descricao.tamanho}")
        String descricao,

        @NotNull(message = "{anuncio.dataHoraPretendida.obrigatoria}")
        Instant dataHoraPretendida,

        @Size(max = 100, message = "{anuncio.tamanhoLocal.tamanho}")
        String tamanhoLocal,

        @Positive(message = "{anuncio.numeroPessoas.positivo}")
        Integer numeroPessoas,

        @Size(max = 600, message = "{anuncio.observacoes.tamanho}")
        String observacoes) {

    public EditarAnuncioRequest {
        titulo = titulo == null ? null : titulo.strip();
        descricao = descricao == null ? null : descricao.strip();
        tamanhoLocal = semTexto(tamanhoLocal) ? null : tamanhoLocal.strip();
        observacoes = semTexto(observacoes) ? null : observacoes.strip();
    }

    private static boolean semTexto(String texto) {
        return texto == null || texto.isBlank();
    }
}
