package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * A renovação mensal do GOLD foi paga (ETI-85). Pede o aviso "Cobrança GOLD" da tela 43 ("Recibo de R$ 29,90
 * disponível. Próxima cobrança em 30/09."), que é do card de notificações (US56); o {@code financeiro} também
 * escuta, para mandar o recibo por e-mail.
 *
 * @param proximaCobrancaEm fim do ciclo que acabou de ser pago
 */
public record CobrancaGoldAprovadaEvent(UUID usuarioId, String nomePlano, long valorCentavos, String moeda,
        Instant proximaCobrancaEm, String numeroRecibo, Instant ocorridoEm) {
}
