package com.limio.api.modulos.moderacao.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.moderacao.enums.StatusResolucao;

/** Resposta da decisão (ETI-116): a denúncia resolvida, fora da fila, e a decisão gravada. */
public record DenunciaDecididaResponse(UUID id, Long numero, StatusResolucao statusResolucao, Instant analisadaEm,
        DecisaoModeracaoResponse decisao) {
}
