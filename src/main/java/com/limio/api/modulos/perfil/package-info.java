/**
 * Perfil de atuação do usuário por papel — dado de exibição/atuação no
 * marketplace, nunca credencial (isso é {@code modulos.auth}).
 *
 * Esqueleto (TICKET-0044) — implementação de entidade/usecase fica pra
 * ticket futuro, um de cada vez, seguindo ADR-0001. Entidades previstas
 * pelo DER (coluna {@code usuario_id} crua, nunca relação JPA pro
 * {@code Usuario} de {@code modulos.auth} — ADR-0001 §1.6):
 *
 * <ul>
 *   <li>{@code perfil_empregador} — nome de exibição, média/qtd avaliações</li>
 *   <li>{@code perfil_prestador} — idem + raio de atendimento, categorias, cota mensal</li>
 *   <li>{@code endereco_salvo} — subentidade de {@code perfil_empregador}</li>
 *   <li>{@code categoria}, {@code prestador_categoria} — taxonomia de atuação</li>
 *   <li>{@code janela_atendimento}, {@code cota_mensal} — subentidade de {@code perfil_prestador}</li>
 * </ul>
 *
 * <p>Nome de produto correto é "empregador", nunca "contratante" ou "solicitante" — DER/diagrama de classes
 * anexados na ADR-0001 ainda mostram {@code perfil_contratante}/{@code contratante_id} (ferramenta externa, não
 * editável por aqui); ao implementar este módulo, usar {@code empregador} nas colunas/tabelas novas.</p>
 */
package com.limio.api.modulos.perfil;
