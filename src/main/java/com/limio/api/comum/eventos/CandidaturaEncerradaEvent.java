package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma candidatura saiu de cena antes da escolha — hoje só pela desistência do Prestador (ETI-77). Único
 * consumidor: {@code chat} deixa o Chat 1:1 da candidatura em somente leitura ({@code
 * DeixarChatSomenteLeituraUseCase}, ele guarda {@code candidaturaId} desde o {@code CandidaturaCriadaEvent},
 * ETI-75).
 */
public record CandidaturaEncerradaEvent(UUID candidaturaId, UUID anuncioId, UUID prestadorId, Instant ocorridoEm) {
}
