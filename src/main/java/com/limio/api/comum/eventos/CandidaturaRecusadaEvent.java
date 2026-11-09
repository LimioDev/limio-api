package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O Empregador recusou uma candidatura (ETI-67). Consumidores previstos, nenhum em código ainda: {@code chat} deixa o
 * chat da candidatura somente leitura; {@code notificacao} avisará o Prestador (ETI-112). {@code anuncioTitulo} vai
 * solto pelo mesmo motivo do {@link CandidaturaCriadaEvent}: quem escuta não pode importar {@code modulos.anuncio}.
 */
public record CandidaturaRecusadaEvent(
        UUID candidaturaId,
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        Instant ocorridoEm) {
}
