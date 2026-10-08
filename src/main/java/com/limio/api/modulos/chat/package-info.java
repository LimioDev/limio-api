/**
 * Conversa entre as partes de uma {@code aplicacao} (módulo {@code anuncio}):
 * chat, mensagem, mídia anexada, detecção de tentativa de contato externo
 * (anti-burla de taxa de intermediação).
 *
 * Esqueleto (TICKET-0044) — implementação de entidade/usecase fica pra
 * ticket futuro, um de cada vez, seguindo ADR-0001. Entidades previstas
 * pelo DER (coluna {@code usuario_id}/{@code aplicacao_id} crua, nunca
 * relação JPA cruzando módulo — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code chat} — 1:1 com {@code aplicacao}</li>
 *   <li>{@code mensagem} — subentidade de {@code chat}, remetente é {@code usuario_id}</li>
 *   <li>{@code tentativa_contato} — subentidade de {@code mensagem} (telefone/e-mail detectado no texto)</li>
 * </ul>
 */
package com.limio.api.modulos.chat;
