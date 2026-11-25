package com.limio.api.comum.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * O administrador ocultou uma avaliação numa decisão de denúncia (ETI-116). Como no {@link AvaliacaoPublicadaEvent},
 * vai com o total já recalculado por {@code moderacao}, agora sem a avaliação oculta. {@code perfil} escuta na mesma
 * transação e só grava a média e a quantidade.
 */
public record AvaliacaoOcultadaEvent(
        UUID avaliadoId,
        PapelUsuario papelAvaliado,
        BigDecimal mediaAvaliacoes,
        int qtdAvaliacoes,
        Instant ocorridoEm) {
}
