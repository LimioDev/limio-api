package com.limio.api.comum.portas;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Porta de leitura das avaliações recebidas pelo Prestador para módulos que não podem importar o módulo dono delas
 * (hoje os candidatos do anúncio — ETI-67). Só consulta.
 */
public interface ConsultaAvaliacoes {

    /**
     * Em lote, pra não consultar um Prestador por vez.
     *
     * @return resumo por id de usuário; quem nunca foi avaliado pode não aparecer no mapa
     */
    Map<UUID, ResumoAvaliacoes> resumir(Collection<UUID> prestadorIds);
}
