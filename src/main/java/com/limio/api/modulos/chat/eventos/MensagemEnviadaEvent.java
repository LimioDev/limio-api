package com.limio.api.modulos.chat.eventos;

import java.util.UUID;

import com.limio.api.modulos.chat.mensagem.Mensagem;

/** Mensagem gravada; depois do commit vai em tempo real para {@code destinatarioId}. Interno do módulo. */
public record MensagemEnviadaEvent(UUID destinatarioId, Mensagem mensagem) {
}
