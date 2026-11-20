package com.limio.api.modulos.chat.records;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;
import com.limio.api.modulos.chat.enums.PapelNoChat;

/**
 * Um item de {@code GET /chats}. {@code candidaturaId} abre o chat em {@code GET /candidaturas/{id}/chat}.
 * {@code statusCandidatura} dá o selo da lista. {@code ultimaMensagem} é {@code null} enquanto ninguém escreveu, e
 * {@code motivoSomenteLeitura}, enquanto o chat recebe mensagens.
 */
public record ConversaResponse(
        UUID chatId,
        UUID candidaturaId,
        OutraParte outraParte,
        String tituloAnuncio,
        String statusCandidatura,
        UltimaMensagem ultimaMensagem,
        long mensagensNaoLidas,
        @JsonProperty("isSomenteLeitura") boolean somenteLeitura,
        MotivoSomenteLeitura motivoSomenteLeitura) {

    /** {@code usuarioId} abre o perfil público da outra pessoa (ETI-136), como no {@code ChatResponse}. */
    public record OutraParte(UUID usuarioId, String nome, String fotoUrl, Instant fotoExpiraEm, PapelNoChat papel) {
    }

    public record UltimaMensagem(String texto, Instant enviadaEm, boolean enviadaPorMim) {
    }
}
