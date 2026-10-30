package com.limio.api.comum.portas;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura de uma candidatura e do anúncio dela, para módulos que não podem importar {@code modulos.anuncio}
 * (hoje o cabeçalho do chat, ETI-101). Só consulta; quem implementa é {@code anuncio}.
 */
public interface ConsultaCandidatura {

    /**
     * @param candidaturaId id da candidatura ({@code aplicacao})
     * @return o resumo, ou vazio se a candidatura ou o anúncio dela não existem
     */
    Optional<ResumoCandidatura> buscarPorId(UUID candidaturaId);

}
