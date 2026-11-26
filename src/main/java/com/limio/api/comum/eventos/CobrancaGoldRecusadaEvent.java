package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O gateway recusou a cobrança da renovação mensal do GOLD (ETI-85, UC "Falha de cobrança e retry"). Pede o aviso
 * de pagamento recusado, que é do card de notificações (US56).
 *
 * @param tentativa número da tentativa recusada dentro do ciclo (1, 2, ...)
 * @param proximaTentativaEm quando a cobrança sai de novo; nulo quando as tentativas acabaram e o Prestador voltou
 *        para FREE
 */
public record CobrancaGoldRecusadaEvent(UUID usuarioId, int tentativa, Instant proximaTentativaEm,
        Instant ocorridoEm) {

    public boolean voltouParaFree() {
        return proximaTentativaEm == null;
    }
}
