package com.limio.api.modulos.notificacao.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Remover o aparelho do push no logout (ETI-112): o mesmo token que o app registrou. */
public record RemoverAparelhoPushRequest(
        @NotBlank(message = "{aparelhoPush.token.obrigatorio}")
        @Size(max = 255, message = "{aparelhoPush.token.tamanho}")
        String token) {
}
