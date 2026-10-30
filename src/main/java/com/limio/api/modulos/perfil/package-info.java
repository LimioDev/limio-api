/**
 * Perfil de atuação do usuário por papel — dado de exibição/atuação no
 * marketplace, nunca credencial (isso é {@code modulos.auth}).
 *
 * Esqueleto criado no TICKET-0044; implementação entra um ticket por vez,
 * seguindo ADR-0001. Entidades previstas pelo DER (coluna {@code usuario_id}
 * crua, nunca relação JPA pro {@code Usuario} de {@code modulos.auth} —
 * ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code perfil_empregador} — nome de exibição, média/qtd avaliações</li>
 *   <li>{@code perfil_prestador} — idem + raio de atendimento, categorias, cota mensal
 *       (TICKET-0054: perfil de atuação — raio, município-base, especialidades, janelas, {@code completo})</li>
 *   <li>{@code endereco_salvo} — subentidade de {@code perfil_empregador}</li>
 *   <li>{@code categoria}, {@code prestador_categoria} — taxonomia de atuação (TICKET-0054)</li>
 *   <li>{@code janela_atendimento} (TICKET-0054), {@code cota_mensal} (US24) — subentidade de {@code perfil_prestador}</li>
 *   <li>{@code midia} — arquivo guardado no storage; hoje só a foto de perfil (TICKET-0056). Sobe pra
 *       {@code comum/} quando {@code anuncio}/{@code chat} também usarem</li>
 * </ul>
 *
 * <p>Nome de produto correto é "empregador", nunca "contratante" ou "solicitante" — DER/diagrama de classes
 * anexados na ADR-0001 ainda mostram {@code perfil_contratante}/{@code contratante_id} (ferramenta externa, não
 * editável por aqui); ao implementar este módulo, usar {@code empregador} nas colunas/tabelas novas.</p>
 */
package com.limio.api.modulos.perfil;
