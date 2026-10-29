package com.limio.api.comum.portas;

import java.util.UUID;

/**
 * {@code status} é o nome do {@code StatusAplicacao} de {@code anuncio} ({@code ENVIADA}, {@code ESCOLHIDA}, ...): texto
 * porque o enum não pode sair do módulo.
 */
public record ResumoCandidatura(UUID id, String status, String anuncioTitulo, String categoriaNome) {
}
