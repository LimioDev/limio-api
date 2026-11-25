package com.limio.api.modulos.moderacao.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.moderacao.enums.StatusResolucao;

/** Confirmação de {@code POST /denuncias}: {@code numero} é o número da denúncia mostrado ao usuário. */
public record DenunciaRegistradaResponse(UUID id, long numero, StatusResolucao statusResolucao, Instant prazoAnalise) {
}
