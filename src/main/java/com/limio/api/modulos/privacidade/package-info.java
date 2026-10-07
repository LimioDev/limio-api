/**
 * Compliance LGPD: exportação de dados do usuário sob demanda.
 *
 * Esqueleto (TICKET-0044) — implementação de entidade/usecase fica pra
 * ticket futuro, um de cada vez, seguindo ADR-0001. Entidade prevista pelo
 * DER (coluna {@code usuario_id} crua, nunca relação JPA pro {@code Usuario}
 * de {@code modulos.auth} — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code exportacao_dado} — pedido de exportação, status, arquivo gerado, expiração</li>
 * </ul>
 */
package com.limio.api.modulos.privacidade;
