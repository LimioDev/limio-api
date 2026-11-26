package com.limio.api.modulos.financeiro.assinaturagold.cobranca;

import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.ResultadoAvisoPagamento;

/**
 * Aviso do gateway sobre uma cobrança (ETI-85), já traduzido do formato do provedor. Identifica a cobrança pela chave
 * de idempotência que o LIMIO mandou ao cobrar e, na falta dela, pelo id da cobrança no gateway.
 */
public record AvisoPagamento(ResultadoAvisoPagamento resultado, String chaveIdempotencia, String gatewayCobrancaId) {
}
