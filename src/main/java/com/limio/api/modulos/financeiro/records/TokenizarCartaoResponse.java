package com.limio.api.modulos.financeiro.records;

/** {@code gatewayToken} é o que o cliente envia depois em {@code AssinarGoldRequest.gatewayToken}. */
public record TokenizarCartaoResponse(String gatewayToken, String bandeira, String ultimos4) {
}
