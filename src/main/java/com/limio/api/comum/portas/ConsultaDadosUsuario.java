package com.limio.api.comum.portas;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura dos dados de contato do usuário para módulos que não podem importar
 * {@code modulos.auth}. Só consulta, nunca altera; quem implementa é o módulo dono do dado.
 *
 * <p>Contrato (vale para qualquer implementação):
 * <ul>
 *   <li>Conta encerrada é tratada como inexistente: {@link #buscarPorId} devolve
 *       {@link Optional#empty()}, igual a um id que não existe. Quem consome não distingue os dois
 *       casos e não recebe dado de conta anonimizada.</li>
 *   <li>{@code DadosUsuario.telefone()} sai em formato internacional, {@code +55} seguido dos dígitos
 *       nacionais (ex.: {@code +5511999998888}). O formato armazenado no banco não é exposto. É
 *       {@code null} se o usuário não tem telefone.</li>
 *   <li>{@code emailConfirmadoEm()} é {@code null} enquanto o e-mail não foi confirmado; uma troca de
 *       e-mail volta o valor a {@code null}. Telefone não tem confirmação (ETI-134).</li>
 * </ul>
 */
public interface ConsultaDadosUsuario {

    /**
     * @param usuarioId id do usuário
     * @return os dados do usuário, ou vazio se ele não existe ou a conta foi encerrada
     */
    Optional<DadosUsuario> buscarPorId(UUID usuarioId);

}
