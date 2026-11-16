package com.limio.api.comum.portas;

import java.util.UUID;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * Porta de leitura de quantos serviços um usuário já concluiu, pro histórico resumido do perfil público
 * (ETI-105), para módulos que não podem importar {@code modulos.anuncio}. Só consulta; quem implementa é o
 * {@code anuncio}, dono do ciclo de vida do serviço.
 */
public interface ConsultaHistoricoDeServicos {

    /**
     * @param usuarioId id do usuário
     * @param papel como contar: {@code EMPREGADOR} conta anúncios concluídos como contratante,
     *              {@code PRESTADOR} conta candidaturas concluídas como prestador escolhido
     * @return quantidade de serviços concluídos; nunca negativo
     */
    int contarConcluidos(UUID usuarioId, PapelUsuario papel);
}
