package com.limio.api.modulos.moderacao.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.moderacao.enums.TipoSancao;

/** Sanção aplicada (ETI-116). {@code inicioEm}/{@code fimEm} só na suspensão; {@code avaliacaoId} só ao ocultar. */
public record SancaoResponse(UUID id, TipoSancao tipo, String motivo, Instant inicioEm, Instant fimEm,
        UUID avaliacaoId, Instant aplicadaEm) {
}
