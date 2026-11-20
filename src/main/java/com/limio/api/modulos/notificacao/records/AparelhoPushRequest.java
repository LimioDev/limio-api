package com.limio.api.modulos.notificacao.records;

import com.limio.api.modulos.notificacao.enums.Plataforma;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registrar o aparelho pra push (ETI-112). {@code token} é o {@code ExponentPushToken[...]} que o {@code
 * expo-notifications} entrega ao app. Texto das mensagens em {@code ValidationMessages.properties}.
 */
public record AparelhoPushRequest(
        @NotBlank(message = "{aparelhoPush.token.obrigatorio}")
        @Size(max = 255, message = "{aparelhoPush.token.tamanho}")
        @Pattern(regexp = "^Expo(nent)?PushToken\\[[^\\]]+\\]$", message = "{aparelhoPush.token.formato}")
        String token,

        @NotNull(message = "{aparelhoPush.plataforma.obrigatoria}")
        Plataforma plataforma) {
}
