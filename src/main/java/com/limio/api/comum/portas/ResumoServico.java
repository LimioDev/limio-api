package com.limio.api.comum.portas;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * {@code prestadorId} é o Prestador escolhido, {@code null} enquanto ninguém foi escolhido. {@code concluidoEm} só vem
 * preenchido com o anúncio {@code CONCLUIDO} (por validação ou por silêncio).
 */
public record ResumoServico(UUID anuncioId, String titulo, UUID contratanteId, UUID prestadorId,
        Instant concluidoEm) {

    public boolean concluido() {
        return concluidoEm != null;
    }

    /** Empregador dono do anúncio ou Prestador escolhido. */
    public boolean participa(UUID usuarioId) {
        return contratanteId.equals(usuarioId) || usuarioId.equals(prestadorId);
    }

    /** Papel que {@code usuarioId}, uma das partes, teve neste serviço. */
    public PapelUsuario papelDe(UUID usuarioId) {
        return contratanteId.equals(usuarioId) ? PapelUsuario.EMPREGADOR : PapelUsuario.PRESTADOR;
    }

    /** Quem está do outro lado para {@code usuarioId}, que precisa ser uma das partes. */
    public UUID outraParte(UUID usuarioId) {
        return contratanteId.equals(usuarioId) ? prestadorId : contratanteId;
    }
}
