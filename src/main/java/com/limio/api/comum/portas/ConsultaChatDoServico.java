package com.limio.api.comum.portas;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura do chat de uma candidatura, do ponto de vista de quem consulta (ETI-86: "serviço
 * combinado"), para módulos que não podem importar {@code modulos.chat}. Só consulta, nunca cria o chat; quem
 * implementa é o {@code chat}.
 */
public interface ConsultaChatDoServico {

    /**
     * @param candidaturaId id da {@code Aplicacao} (mesmo id de {@code Chat.aplicacaoId})
     * @param usuarioId quem está consultando — decide o que conta como "não lida"
     * @return o chat e as mensagens não lidas por {@code usuarioId}; vazio se o chat ainda não existe
     */
    Optional<ResumoChatDoServico> buscar(UUID candidaturaId, UUID usuarioId);
}
