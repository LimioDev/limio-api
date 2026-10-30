package com.limio.api.comum.portas;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Porta de leitura das escolhas de um Prestador para módulos que não podem importar {@code modulos.anuncio} (hoje
 * o {@code perfil}, na lista "Onde você usou sua cota" — US24). Só consulta; quem implementa é o {@code anuncio}.
 */
public interface ConsultaEscolhasDoPrestador {

    /**
     * @param inicio       início do período, incluso
     * @param fimExclusivo fim do período, não incluso
     * @return as candidaturas escolhidas no período, da escolha mais recente pra mais antiga, qualquer que seja o
     *         status atual
     */
    List<EscolhaDoPrestador> listarEscolhidasEntre(UUID prestadorId, Instant inicio, Instant fimExclusivo);
}
