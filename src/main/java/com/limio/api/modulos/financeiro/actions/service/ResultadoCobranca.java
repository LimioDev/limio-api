package com.limio.api.modulos.financeiro.actions.service;

/**
 * Resposta do gateway a uma tentativa de cobrança (ETI-81). {@code bandeira}/{@code ultimos4} só vêm
 * preenchidos quando {@code aprovado}: é o próprio gateway que devolve esses dados na tokenização/cobrança,
 * o LIMIO nunca recebe número de cartão nem CVV pra derivar isso.
 */
public record ResultadoCobranca(boolean aprovado, String gatewayCobrancaId, String bandeira, String ultimos4) {

    public static ResultadoCobranca recusada() {
        return new ResultadoCobranca(false, null, null, null);
    }
}
