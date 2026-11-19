/**
 * Notificação transacional (push/e-mail/sms/whatsapp) e preferência do
 * usuário por canal.
 *
 * Entidades (coluna {@code usuario_id} crua, nunca relação JPA pro
 * {@code Usuario} de {@code modulos.auth} — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code notificacao} — o que a tela 43 lista, ligado a {@code usuario_id} + opcionalmente {@code anuncio_id}
 *       (ETI-110); quem grava é o envio de avisos (ETI-112)</li>
 *   <li>{@code preferencia_notificacao} — 1:1 com {@code usuario_id} (push/email/sms/whatsapp ligado/desligado)</li>
 * </ul>
 */
package com.limio.api.modulos.notificacao;
