package com.limio.api.modulos.chat;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;

public record ConversaResumo(UUID chatId, UUID candidaturaId, OutraParte outraParte, String tituloAnuncio,
        String statusCandidatura, UltimaMensagem ultimaMensagem, long mensagensNaoLidas, boolean somenteLeitura,
        MotivoSomenteLeitura motivoSomenteLeitura) {

    public record UltimaMensagem(String texto, Instant enviadaEm, boolean enviadaPorMim) {
    }
}
