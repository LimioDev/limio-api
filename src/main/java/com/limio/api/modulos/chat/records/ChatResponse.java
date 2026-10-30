package com.limio.api.modulos.chat.records;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.limio.api.modulos.chat.enums.MotivoSomenteLeitura;
import com.limio.api.modulos.chat.enums.PapelNoChat;

/**
 * Resposta de {@code GET /candidaturas/{candidaturaId}/chat} (telas 21 e 22). {@code motivoSomenteLeitura} é
 * {@code null} enquanto o chat recebe mensagens. {@code historico.mensagens} vem da mais nova para a mais antiga.
 */
public record ChatResponse(
        UUID id,
        UUID candidaturaId,
        String statusCandidatura,
        OutraParte outraParte,
        Anuncio anuncio,
        @JsonProperty("isSomenteLeitura") boolean somenteLeitura,
        MotivoSomenteLeitura motivoSomenteLeitura,
        Historico historico) {

    /** {@code nome} é "Usuário excluído", sem foto, quando a conta foi encerrada. */
    public record OutraParte(String nome, String fotoUrl, Instant fotoExpiraEm, PapelNoChat papel) {
    }

    public record Anuncio(String titulo, String categoria) {
    }

    /** Para carregar as anteriores, o app manda o id da última de {@code mensagens} em {@code antesDe}. */
    public record Historico(List<MensagemResponse> mensagens, int tamanho, boolean temMais) {
    }
}
