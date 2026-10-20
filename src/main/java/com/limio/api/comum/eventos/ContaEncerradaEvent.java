package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado por {@code modulos.auth} depois que ele termina sua própria
 * reação ao encerramento de conta (TICKET-0034/ETI-36, LGPD). Canal
 * sancionado pelo ADR-0001 pra um módulo avisar outro sem import direto —
 * quem precisar reagir (perfil, consentimento, ...) escuta via
 * {@code @EventListener}/{@code @TransactionalEventListener}, sem importar
 * {@code modulos.auth}. Fica em {@code comum/} porque, por definição, um
 * evento de domínio entre módulos só funciona se todo módulo que algum dia
 * precisar escutá-lo conseguir importar o tipo — diferente do critério "2+
 * módulos usando hoje" de helper/lógica, que não se aplica ao contrato em si.
 */
public record ContaEncerradaEvent(UUID usuarioId, Instant ocorridoEm) {
}
