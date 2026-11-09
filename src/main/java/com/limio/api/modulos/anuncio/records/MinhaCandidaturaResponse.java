package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.anuncio.enums.StatusAplicacao;

/**
 * Item da tela "Aplicações" (ETI-77). Sem endereço completo nem contatos do Empregador, em nenhuma aba: a
 * tela não precisa e o UC "Consultar histórico" proíbe expor isso onde o Prestador não foi escolhido.
 * {@code nomeEmpregador}/{@code iniciaisEmpregador} saem {@code null} se a conta do Empregador foi encerrada.
 */
public record MinhaCandidaturaResponse(
        UUID id,
        UUID anuncioId,
        String titulo,
        Instant dataHoraPretendida,
        String nomeEmpregador,
        String iniciaisEmpregador,
        StatusAplicacao status) {
}
