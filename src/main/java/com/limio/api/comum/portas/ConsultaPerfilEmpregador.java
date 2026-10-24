package com.limio.api.comum.portas;

import java.util.UUID;

/**
 * Porta de leitura do perfil de exibição do Empregador (nome de exibição e foto) para módulos que não
 * podem importar {@code modulos.perfil}. Quem implementa é o módulo dono do dado.
 *
 * <p>Contrato (vale para qualquer implementação):
 * <ul>
 *   <li>Usuário sem perfil ainda ganha um, com o nome de exibição inicial (mesma regra de
 *       {@code GET /conta/perfil-empregador}).</li>
 *   <li>Conta inexistente ou encerrada lança a {@code NegocioException} de código
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

}
