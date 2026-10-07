/**
 * Log de auditoria genérico de ação sobre qualquer entidade do sistema
 * ({@code entidade} + {@code entidade_id} como par polimórfico).
 *
 * Esqueleto (TICKET-0044) — implementação de entidade/usecase fica pra
 * ticket futuro, um de cada vez, seguindo ADR-0001. Não entra em
 * {@code comum/} porque carrega dado de negócio (ator, ação, detalhe),
 * não é infraestrutura técnica pura (critério ADR-0001 §1.6: "só sobe pra
 * comum/ quando usado por 2+ módulos hoje" não se aplica — isso não é
 * utilitário compartilhado, é o próprio domínio de auditoria).
 *
 * <ul>
 *   <li>{@code log_auditoria} — ator ({@code usuario_id}), entidade+id afetada, tipo de ação, detalhe em JSON</li>
 * </ul>
 */
package com.limio.api.modulos.auditoria;
