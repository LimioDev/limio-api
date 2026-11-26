package com.limio.api.modulos.financeiro.actions.mapper;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.financeiro.assinaturagold.cobranca.AvisoPagamento;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.ResultadoAvisoPagamento;
import com.limio.api.modulos.financeiro.records.AsaasWebhookRequest;

/**
 * Traduz o webhook do Asaas para {@link AvisoPagamento} (ETI-85). Só os eventos que resolvem uma cobrança de cartão
 * viram aviso; os demais (criação, vencimento, estorno, chargeback...) ficam de fora e o webhook só responde 200.
 *
 * <p>Eventos do Asaas: {@code PAYMENT_CONFIRMED} e {@code PAYMENT_RECEIVED} aprovam;
 * {@code PAYMENT_CREDIT_CARD_CAPTURE_REFUSED} e {@code PAYMENT_REPROVED_BY_RISK_ANALYSIS} recusam.
 */
@Component
public class AvisoPagamentoMapper {

    public Optional<AvisoPagamento> toAviso(AsaasWebhookRequest webhook) {
        if (webhook.payment() == null || webhook.event() == null) {
            return Optional.empty();
        }
        return resultado(webhook.event()).map(resultado -> new AvisoPagamento(resultado,
                webhook.payment().externalReference(), webhook.payment().id()));
    }

    private static Optional<ResultadoAvisoPagamento> resultado(String evento) {
        return switch (evento) {
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> Optional.of(ResultadoAvisoPagamento.APROVADO);
            case "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED", "PAYMENT_REPROVED_BY_RISK_ANALYSIS" ->
                    Optional.of(ResultadoAvisoPagamento.RECUSADO);
            default -> Optional.empty();
        };
    }
}
