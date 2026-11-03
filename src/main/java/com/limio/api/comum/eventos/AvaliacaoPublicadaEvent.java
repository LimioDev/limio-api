package com.limio.api.comum.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * Uma avaliação foi gravada (ETI-107). Consumidor: {@code perfil} guarda a média e a quantidade no perfil do papel que
 * o avaliado teve no serviço. Vai com o total já recalculado por {@code moderacao}, dona das avaliações: quem escuta
 * só grava, não soma, e duas avaliações seguidas nunca deixam a média atrás.
 *
 * @param mediaAvaliacoes média de todas as avaliações do avaliado nesse papel, com uma casa decimal
 */
public record AvaliacaoPublicadaEvent(
        UUID avaliadoId,
        PapelUsuario papelAvaliado,
        BigDecimal mediaAvaliacoes,
        int qtdAvaliacoes,
        Instant ocorridoEm) {
}
