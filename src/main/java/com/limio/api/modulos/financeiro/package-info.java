/**
 * Billing: plano, assinatura gold, cobrança, meio de pagamento, documento
 * fiscal.
 *
 * Assinar o plano GOLD (ETI-81) implementou o módulo inteiro, seguindo
 * ADR-0001. Entidades (coluna {@code usuario_id} crua, nunca relação JPA pro
 * {@code Usuario} de {@code modulos.auth} — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code plano} ({@code modulos.financeiro.plano}) — catálogo de planos</li>
 *   <li>{@code assinatura_gold} ({@code modulos.financeiro.assinaturagold}) — assinatura de um usuário a
 *       um plano</li>
 *   <li>{@code cobranca}, {@code documento_fiscal} — subentidades de {@code assinatura_gold}</li>
 *   <li>{@code meio_pagamento} ({@code modulos.financeiro.meiopagamento}) — token de gateway por usuário</li>
 * </ul>
 *
 * <p>Implementa a porta {@code comum/portas/ConsultaPlanoPrestador} (ETI-79) a partir do status real da
 * assinatura. Detalhe em {@code docs/features/ETI-81-assinar-gold.md}.
 */
package com.limio.api.modulos.financeiro;
