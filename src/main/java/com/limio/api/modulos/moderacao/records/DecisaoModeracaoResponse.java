package com.limio.api.modulos.moderacao.records;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.limio.api.modulos.moderacao.enums.VereditoModeracao;

/** A decisão do administrador sobre uma denúncia (ETI-116) e as sanções que ela aplicou. */
public record DecisaoModeracaoResponse(UUID id, VereditoModeracao veredito, String fundamento, UUID administradorId,
        Instant decididaEm, List<SancaoResponse> sancoes) {
}
