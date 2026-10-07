/**
 * Notificação transacional (push/e-mail/sms/whatsapp) e preferência do
 * usuário por canal.
 *
 * Esqueleto (TICKET-0044) — implementação de entidade/usecase fica pra
 * ticket futuro, um de cada vez, seguindo ADR-0001. Entidades previstas
 * pelo DER (coluna {@code usuario_id} crua, nunca relação JPA pro
 * {@code Usuario} de {@code modulos.auth} — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code notificacao} — envio por canal, ligado a {@code usuario_id} + opcionalmente {@code anuncio_id}</li>
 *   <li>{@code preferencia_notificacao} — 1:1 com {@code usuario_id} (push/email/sms/whatsapp ligado/desligado)</li>
 * </ul>
 */
package com.limio.api.modulos.notificacao;
