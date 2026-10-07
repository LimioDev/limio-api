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
 * <p><b>Pendência em aberto (não resolvida nesta ticket, ver ADR-0001
 * revisão 3):</b> {@code PapelUsuario.ADMIN} (enum em {@code modulos.auth})
 * e esta tabela {@code administrador} coexistem no DER sem relação
 * explicada — decidir se {@code ADMIN} é vestigial/não usado ou se os dois
 * caminhos de login administrativo devem de fato existir em paralelo.</p>
 */
package com.limio.api.modulos.administracao;
