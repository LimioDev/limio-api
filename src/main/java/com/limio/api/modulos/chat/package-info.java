/**
 * Conversa entre as partes de uma {@code aplicacao} (módulo {@code anuncio}):
 * chat, mensagem, mídia anexada, detecção de tentativa de contato externo
 * (anti-burla de taxa de intermediação).
 *
 * Entidades (coluna {@code usuario_id}/{@code aplicacao_id} crua, nunca
 * relação JPA cruzando módulo — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code chat} — 1:1 com {@code aplicacao} (ETI-101)</li>
 *   <li>{@code mensagem} — subentidade de {@code chat}, remetente é {@code usuario_id} (ETI-101)</li>
 *   <li>{@code tentativa_contato} — subentidade de {@code mensagem} (telefone/e-mail detectado no texto), ainda
 *       não implementada (US50)</li>
 * </ul>
 */
package com.limio.api.modulos.chat;
