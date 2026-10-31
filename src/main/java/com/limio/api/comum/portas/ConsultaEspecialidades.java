package com.limio.api.comum.portas;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Porta de leitura das especialidades do Prestador para módulos que não podem importar {@code modulos.perfil}
 * (hoje os candidatos do anúncio — ETI-67). Só consulta; quem implementa é o {@code perfil}, dono do perfil de atuação.
 */
public interface ConsultaEspecialidades {

    /**
     * Em lote, pra não consultar um Prestador por vez.
     *
     * @return nomes das especialidades na ordem de exibição, por id de usuário; quem ainda não salvou o perfil de
     * atuação não aparece no mapa
     */
    Map<UUID, List<String>> listarNomes(Collection<UUID> prestadorIds);
}
