package com.limio.api.comum.portas;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura do serviço (o anúncio com o Prestador escolhido) para módulos que não podem importar
 * {@code modulos.anuncio} (hoje a avaliação de {@code moderacao}, ETI-107). Só consulta; quem implementa é
 * {@code anuncio}.
 */
public interface ConsultaServico {

    /**
     * @param anuncioId id do anúncio
     * @return o resumo, ou vazio se o anúncio não existe
     */
    Optional<ResumoServico> buscarPorId(UUID anuncioId);
}
