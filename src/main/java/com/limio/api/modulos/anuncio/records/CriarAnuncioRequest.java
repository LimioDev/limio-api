package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /anuncios} (ETI-59). O endereço é {@code enderecoSalvoId} (um endereço salvo do
 * Empregador) ou {@code enderecoAvulso}, nunca os dois. Não existe campo de valor (UC "sem valor").
 * Data no passado e categoria indisponível são regra de negócio, conferidas no usecase. Texto das mensagens em
 * {@code ValidationMessages.properties}.
 */
public record CriarAnuncioRequest(
        @NotNull(message = "{anuncio.categoriaId.obrigatoria}")
        UUID categoriaId,

        @NotBlank(message = "{anuncio.descricao.obrigatoria}")
        @Size(max = 600, message = "{anuncio.descricao.tamanho}")
        String descricao,

        @NotNull(message = "{anuncio.dataHoraPretendida.obrigatoria}")
        Instant dataHoraPretendida,

        UUID enderecoSalvoId,

        @Valid
        EnderecoAvulsoRequest enderecoAvulso,

        @Size(max = 100, message = "{anuncio.tamanhoLocal.tamanho}")
        String tamanhoLocal,

        @Positive(message = "{anuncio.numeroPessoas.positivo}")
        Integer numeroPessoas,

        @Size(max = 600, message = "{anuncio.observacoes.tamanho}")
        String observacoes) {

    public CriarAnuncioRequest {
        descricao = descricao == null ? null : descricao.strip();
        tamanhoLocal = semTexto(tamanhoLocal) ? null : tamanhoLocal.strip();
        observacoes = semTexto(observacoes) ? null : observacoes.strip();
    }

    /** {@code @JsonIgnore}: é regra de validação, não campo do contrato. */
    @JsonIgnore
    @AssertTrue(message = "{anuncio.endereco.umDosDois}")
    public boolean isUmEndereco() {
        return (enderecoSalvoId == null) != (enderecoAvulso == null);
    }

    private static boolean semTexto(String texto) {
        return texto == null || texto.isBlank();
    }
}
