package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O Prestador escolhido iniciou o serviço (ETI-88). Consumidor previsto, ainda não em código: {@code
 * notificacao} avisa o Empregador (ETI-112). {@code anuncioTitulo} vai solto pelo mesmo motivo de {@link
 * CandidaturaCriadaEvent}: quem escuta não pode importar {@code modulos.anuncio} (ADR-0001).
 */
public record ServicoIniciadoEvent(
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        Instant ocorridoEm) {
}
