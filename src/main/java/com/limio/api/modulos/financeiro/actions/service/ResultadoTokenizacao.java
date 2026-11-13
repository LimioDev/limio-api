package com.limio.api.modulos.financeiro.actions.service;

/**
 * Resposta do gateway a uma tokenização de cartão (ETI-81). {@code gatewayToken} é o que
 * {@code TokenizarCartaoUseCase} salva como {@code MeioPagamento} e o que uma {@code AssinarGoldRequest}
 * futura usa pra cobrar; {@code bandeira}/{@code ultimos4} são só pra exibição.
 */
public record ResultadoTokenizacao(String gatewayToken, String bandeira, String ultimos4) {
}
