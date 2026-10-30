package com.limio.api.comum.portas;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Porta de leitura do chat de uma candidatura para módulos que não podem importar {@code modulos.chat} (hoje os
 * candidatos do anúncio — ETI-67). Só consulta, nunca cria o chat; quem implementa é o {@code chat}.
 */
public interface ConsultaChat {

    /**
     * Em lote, pra não consultar uma candidatura por vez.
     *
     * @return id do chat por id de candidatura; candidatura cujo chat ainda não foi criado não aparece no mapa
     */
    Map<UUID, UUID> buscarIdsPorCandidatura(Collection<UUID> candidaturaIds);
}
