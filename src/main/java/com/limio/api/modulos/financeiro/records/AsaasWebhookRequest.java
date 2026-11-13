package com.limio.api.modulos.financeiro.records;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Payload do webhook do Asaas (ETI-81) — só os campos que {@code AsaasWebhookController} loga hoje. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasWebhookRequest(String event, Pagamento payment) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pagamento(String id, String status, String customer, String externalReference) {
    }
}
