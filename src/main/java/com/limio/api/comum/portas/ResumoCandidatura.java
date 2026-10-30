package com.limio.api.comum.portas;

import java.util.UUID;

/**
 * {@code status} é o nome do {@code StatusAplicacao} de {@code anuncio} ({@code ENVIADA}, {@code ESCOLHIDA}, ...): texto
 * porque o enum não pode sair do módulo. {@code contratanteId} é o dono do anúncio e {@code prestadorId} quem se
 * candidatou.
 */
public record ResumoCandidatura(UUID id, String status, UUID anuncioId, UUID contratanteId, UUID prestadorId,
        String anuncioTitulo, String categoriaNome) {

    public boolean participa(UUID usuarioId) {
        return contratanteId.equals(usuarioId) || prestadorId.equals(usuarioId);
    }
}
