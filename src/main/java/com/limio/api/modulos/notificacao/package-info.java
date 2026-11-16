/**
 * Notificação transacional (push/e-mail/whatsapp) e preferência do
 * usuário por canal. Os outros módulos só publicam eventos de aviso; este
 * grava a notificação e envia pelos canais ligados (ETI-112).
 *
 * Entidades (coluna {@code usuario_id} crua, nunca relação JPA pro
 * {@code Usuario} de {@code modulos.auth} — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code notificacao} — o que a tela 43 lista, ligado a {@code usuario_id} + opcionalmente {@code anuncio_id}
 *       (ETI-110); quem grava é o envio de avisos (ETI-112)</li>
 *   <li>{@code preferencia_notificacao} — 1:1 com {@code usuario_id} (push/email/whatsapp ligado/desligado)</li>
 *   <li>{@code envio_notificacao} — por qual canal cada notificação saiu ou falhou (ETI-112)</li>
 *   <li>{@code aparelho_push} — token de push do aparelho logado (ETI-112)</li>
 * </ul>
 */
package com.limio.api.modulos.notificacao;
