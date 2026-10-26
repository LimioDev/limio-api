/**
 * Backoffice: login de administrador (ator próprio, não é {@code Usuario}
 * de {@code modulos.auth}) e parâmetro de sistema configurável.
 *
 * Esqueleto (TICKET-0044) — implementação de entidade/usecase fica pra
 * ticket futuro, um de cada vez, seguindo ADR-0001. Entidades previstas
 * pelo DER:
 *
 * <ul>
 *   <li>{@code administrador} — login próprio (nome/email/senha_hash), ator distinto de {@code Usuario}</li>
 *   <li>{@code parametro}, {@code parametro_historico} — configuração versionada do sistema</li>
 * </ul>
 *
 * <p><b>Decisão (tech lead, 2026-10-26):</b> administrador é ator próprio, com login
 * nesta tabela {@code administrador}. {@code PapelUsuario.ADMIN} foi removido
 * (migration V15) — não existe caminho de login administrativo por
 * {@code Usuario}.</p>
 */
package com.limio.api.modulos.administracao;
