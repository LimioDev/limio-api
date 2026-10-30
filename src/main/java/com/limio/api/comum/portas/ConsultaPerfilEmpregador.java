package com.limio.api.comum.portas;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura do perfil de exibição do Empregador (nome de exibição e foto) para módulos que não
 * podem importar {@code modulos.perfil}. Quem implementa é o módulo dono do dado.
 *
 * <p>Contrato (vale para qualquer implementação):
 * <ul>
 *   <li>{@link #buscarResumo}: usuário sem perfil ainda ganha um, com o nome de exibição inicial (mesma regra de
 *       {@code GET /conta/perfil-empregador}).</li>
 *   <li>{@link #buscarResumo}: conta inexistente ou encerrada lança a {@code NegocioException} de código
 *       {@code PERFIL_EMPREGADOR_NAO_ENCONTRADO} (404), sem criar perfil.</li>
 *   <li>{@code fotoUrl}/{@code fotoExpiraEm} são {@code null} se o usuário não tem foto ou se o link não
 *       pôde ser gerado agora: a foto nunca derruba a consulta.</li>
 * </ul>
 */
public interface ConsultaPerfilEmpregador {

    /**
     * @param usuarioId id do usuário
     * @return nome de exibição e link temporário da foto
     */
    ResumoPerfilEmpregador buscarResumo(UUID usuarioId);

    /**
     * Só leitura, para quem mostra o Empregador a outra pessoa (cabeçalho do chat): usuário sem perfil aparece com o
     * nome de exibição inicial, sem que o perfil seja criado.
     *
     * @param usuarioId id do usuário
     * @return nome de exibição e link temporário da foto, ou vazio se a conta não existe ou foi encerrada
     */
    Optional<ResumoPerfilEmpregador> buscarResumoSemCriar(UUID usuarioId);

    /**
     * Versão em lote de {@link #buscarResumo}, para listagens (uma consulta por tabela, não uma por item).
     * Só leitura: diferente de {@code buscarResumo}, não cria perfil — quem nunca abriu o perfil
     * aparece com o nome de exibição inicial, calculado na hora. Conta inexistente ou encerrada simplesmente
     * não vem no mapa (sem exceção). A foto segue a mesma regra: nunca derruba a consulta.
     *
     * @param usuarioIds ids dos usuários (repetidos são ignorados)
     * @return resumo por id de usuário
     */
    Map<UUID, ResumoPerfilEmpregador> buscarResumosEmLote(Collection<UUID> usuarioIds);

}
