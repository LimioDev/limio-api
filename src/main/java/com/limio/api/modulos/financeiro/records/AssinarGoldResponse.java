package com.limio.api.modulos.financeiro.records;

import java.time.Instant;

import com.limio.api.modulos.financeiro.assinaturagold.enums.StatusAssinatura;

public record AssinarGoldResponse(StatusAssinatura status, Instant renovaEm) {
}
