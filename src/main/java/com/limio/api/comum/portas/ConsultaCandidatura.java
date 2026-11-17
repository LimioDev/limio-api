package com.limio.api.comum.portas;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de leitura de uma candidatura e do anúncio dela, para módulos que não podem importar {@code modulos.anuncio}
 * (o cabeçalho do chat, ETI-101, e os avisos da escolha, ETI-112). Só consulta; quem implementa é {@code anuncio}.
 */
public interface ConsultaCandidatura {

    /**
     * @param candidaturaId id da candidatura ({@code aplicacao})
     * @return o resumo, ou vazio se a candidatura ou o anúncio dela não existem
     */
    Optional<ResumoCandidatura> buscarPorId(UUID candidaturaId);

    /**
     * Numa consulta só (avisos da escolha, ETI-112).
     *
     * @param candidaturaIds ids das candidaturas ({@code aplicacao})
     * @return quem se candidatou em cada uma; candidatura que não existe fica de fora
     */
    List<UUID> buscarPrestadoresIds(Collection<UUID> candidaturaIds);

}
