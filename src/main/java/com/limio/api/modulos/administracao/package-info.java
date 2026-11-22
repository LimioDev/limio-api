/**
 * Backoffice: login de administrador (ator próprio, não é {@code Usuario}
 * de {@code modulos.auth}) e parâmetro de sistema configurável.
 *
 * Entidades previstas pelo DER:
 *
 * <ul>
 *   <li>{@code administrador} — ator distinto de {@code Usuario} (nome/email/senha_hash). Tabela e
 *       token desde o ETI-117 (dashboard). A rota de login fica para o card da US60.</li>
 *   <li>{@code parametro}, {@code parametro_historico} — configuração versionada do sistema (ainda não implementadas)</li>
 * </ul>
 *
 * <p><b>Decisão (tech lead, 2026-10-26):</b> administrador é ator próprio, com login
 * nesta tabela {@code administrador}. {@code PapelUsuario.ADMIN} foi removido
 * (migration V15) — não existe caminho de login administrativo por
 * {@code Usuario}.</p>
 */
package com.limio.api.modulos.administracao;
