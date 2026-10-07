/**
 * Billing: plano, assinatura gold, cobrança, meio de pagamento, documento
 * fiscal.
 *
 * Esqueleto (TICKET-0044) — implementação de entidade/usecase fica pra
 * ticket futuro, um de cada vez, seguindo ADR-0001. Entidades previstas
 * pelo DER (coluna {@code usuario_id} crua, nunca relação JPA pro
 * {@code Usuario} de {@code modulos.auth} — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code plano} — catálogo de planos</li>
 *   <li>{@code assinatura_gold} — assinatura de um {@code perfil_prestador} a um plano</li>
 *   <li>{@code cobranca}, {@code documento_fiscal} — subentidade de {@code assinatura_gold}</li>
 *   <li>{@code meio_pagamento} — token de gateway por usuário</li>
 * </ul>
 */
package com.limio.api.modulos.financeiro;
