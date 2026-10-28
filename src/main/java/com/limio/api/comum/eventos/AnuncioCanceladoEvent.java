package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Empregador cancelou o anúncio antes de escolher alguém (ETI-63). Único consumidor previsto, ainda não
 * em código: {@code chat} marca {@code isSomenteLeitura} em todos os chats do anúncio — ele já guarda
 * {@code anuncioId} cru (recebido no {@code CandidaturaCriadaEvent} de cada candidatura, ETI-75), então
 * não precisa da lista de candidaturas aqui, só o id do anúncio.
 */
public record AnuncioCanceladoEvent(UUID anuncioId, Instant ocorridoEm) {
}
