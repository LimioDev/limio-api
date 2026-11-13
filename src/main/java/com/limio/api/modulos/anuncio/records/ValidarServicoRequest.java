package com.limio.api.modulos.anuncio.records;

import com.limio.api.modulos.anuncio.enums.ModoValidacao;

import jakarta.validation.constraints.NotNull;

/** Corpo de {@code POST /anuncios/{anuncioId}/validar} (ETI-92). */
public record ValidarServicoRequest(
        @NotNull(message = "{anuncio.validarServico.modo.obrigatorio}")
        ModoValidacao modo) {
}
