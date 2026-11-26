package com.limio.api.modulos.financeiro.records;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload do webhook do Asaas (ETI-81) — só os campos que o LIMIO usa. {@code payment.externalReference} é a chave de
 * idempotência que o LIMIO mandou ao cobrar, e é por ela que o aviso acha a cobrança (ETI-85).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasWebhookRequest(String event, Pagamento payment) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagamento(String id, String status, String customer, String externalReference) {
    }
}
