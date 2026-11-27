package com.limio.api.modulos.financeiro.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.limio.api.modulos.financeiro.actions.mapper.AvisoPagamentoMapper;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.AvisoPagamento;
import com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums.ResultadoAvisoPagamento;
import com.limio.api.modulos.financeiro.records.AsaasWebhookRequest;

class AvisoPagamentoMapperTest {

    private final AvisoPagamentoMapper mapper = new AvisoPagamentoMapper();

    private static AsaasWebhookRequest webhook(String evento) {
        return new AsaasWebhookRequest(evento,
                new AsaasWebhookRequest.Pagamento("pay_1", "CONFIRMED", "cus_1", "chave-1"));
    }

    @ParameterizedTest
    @CsvSource({
            "PAYMENT_CONFIRMED, APROVADO",
            "PAYMENT_RECEIVED, APROVADO",
            "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED, RECUSADO",
            "PAYMENT_REPROVED_BY_RISK_ANALYSIS, RECUSADO"
    })
    void dadoEventoQueResolveACobranca_quandoTraduz_entaoViraAviso(String evento, ResultadoAvisoPagamento esperado) {
        assertThat(mapper.toAviso(webhook(evento)))
                .contains(new AvisoPagamento(esperado, "chave-1", "pay_1"));
    }

    @Test
    void dadoOutroEvento_quandoTraduz_entaoFicaDeFora() {
        assertThat(mapper.toAviso(webhook("PAYMENT_REFUNDED"))).isEmpty();
        assertThat(mapper.toAviso(webhook(null))).isEmpty();
        assertThat(mapper.toAviso(new AsaasWebhookRequest("PAYMENT_CONFIRMED", null))).isEmpty();
    }
}
