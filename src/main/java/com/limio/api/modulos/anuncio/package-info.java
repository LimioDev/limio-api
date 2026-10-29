/**
 * Ciclo de vida do anúncio de serviço: publicação, aplicação de prestador,
 * agendamento/reagendamento, contestação e valor combinado.
 *
 * Esqueleto (TICKET-0044) — implementação de entidade/usecase fica pra
 * ticket futuro, um de cada vez, seguindo ADR-0001. Entidades previstas
 * pelo DER (coluna {@code usuario_id}/{@code perfil_*_id} crua, nunca
 * relação JPA pro {@code Usuario} de {@code modulos.auth} — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code anuncio} — agregado raiz</li>
 *   <li>{@code aplicacao} — candidatura de prestador a um anúncio</li>
 *   <li>{@code valor_combinado}, {@code reagendamento}, {@code nao_comparecimento}, {@code contestacao} — subentidades de {@code anuncio}</li>
 *   <li>{@code midia} — mídia anexada a anúncio/aplicação (também referenciada por {@code chat}). As fotos
 *       do anúncio (ETI-59) usam a entity {@code comum.midia.Midia}, que subiu de {@code modulos.perfil}
 *       quando este módulo passou a gravar mídia (ADR-0001: nunca importar {@code perfil})</li>
 * </ul>
 */
package com.limio.api.modulos.anuncio;
