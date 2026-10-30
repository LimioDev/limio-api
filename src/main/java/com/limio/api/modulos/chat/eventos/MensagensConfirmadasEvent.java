package com.limio.api.modulos.chat.eventos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.limio.api.modulos.chat.enums.Confirmacao;

/** A outra parte confirmou mensagens; depois do commit o aviso vai em tempo real para {@code remetenteId}. Interno. */
public record MensagensConfirmadasEvent(UUID chatId, UUID remetenteId, Confirmacao confirmacao,
        List<UUID> mensagemIds, Instant em) {
}
