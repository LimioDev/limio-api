package com.limio.api.comum.portas;

import java.util.UUID;

/**
 * Mensagem de chat vista de fora do {@code chat}: quem enviou e as duas partes da conversa. {@code candidaturaId} e
 * {@code anuncioId} são o contexto da conversa (o chat é 1:1 por candidatura).
 */
public record ResumoMensagem(UUID id, UUID remetenteId, UUID candidaturaId, UUID anuncioId, UUID contratanteId,
        UUID prestadorId) {

    /** Empregador dono do anúncio ou Prestador da candidatura: as duas pessoas que veem a mensagem. */
    public boolean participa(UUID usuarioId) {
        return contratanteId.equals(usuarioId) || prestadorId.equals(usuarioId);
    }
}
