/**
 * Reputação e disciplina: avaliação entre as partes, denúncia, sanção,
 * decisão de moderação, recurso — e bloqueio social entre usuários (não é
 * suspensão de conta, isso é {@code StatusConta.BLOQUEADA} em
 * {@code modulos.auth}).
 *
 * Esqueleto (TICKET-0044) — implementação de entidade/usecase fica pra
 * ticket futuro, um de cada vez, seguindo ADR-0001. Entidades previstas
 * pelo DER (coluna {@code usuario_id} crua, nunca relação JPA pro
 * {@code Usuario} de {@code modulos.auth} — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code avaliacao} — nota/comentário entre avaliador e avaliado de um anúncio</li>
 *   <li>{@code denuncia} — denunciante vs. denunciado</li>
 *   <li>{@code sancao}, {@code decisao_moderacao}, {@code recurso} — subentidades de {@code denuncia}</li>
 *   <li>{@code bloqueio_usuario} — usuário bloqueia usuário (bloqueador_id/bloqueado_id), não é suspensão de conta</li>
 * </ul>
 */
package com.limio.api.modulos.moderacao;
