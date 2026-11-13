package com.limio.api.modulos.financeiro.actions.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.modulos.financeiro.actions.helper.WebhookTokenHelper;
import com.limio.api.modulos.financeiro.records.AsaasWebhookRequest;

/**
 * Recebe os eventos do Asaas (ETI-81) — cobrança de cartão já é confirmada na hora pela chamada síncrona
 * de {@code AssinarGoldUseCase}; este webhook serve pro que só o Asaas sabe depois (reembolso,
 * chargeback, renovação automática futura). Hoje só loga: nenhuma dessas reações tem usecase ainda.
 *
 * <p>Sem JWT — Asaas não tem o token do LIMIO. Autenticidade é o header {@code asaas-access-token}
 * batendo com {@code asaas.webhook.token} (configurado como "Token de autenticação" no cadastro do
 * webhook no painel Asaas, comparado em tempo constante por {@link WebhookTokenHelper}). Rota liberada
 * em {@code SecurityConfig}.
 *
 * <p>Só existe quando {@code financeiro.gateway.provedor=asaas} (Railway) — local/teste, com o gateway
 * simulado, não precisa do segredo deste webhook.
 */
@RestController
@RequestMapping("/financeiro/gold/webhook")
@ConditionalOnProperty(name = "financeiro.gateway.provedor", havingValue = "asaas")
public class AsaasWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AsaasWebhookController.class);

    private final String tokenEsperado;

    public AsaasWebhookController(@Value("${asaas.webhook.token}") String tokenEsperado) {
        this.tokenEsperado = tokenEsperado;
    }

    @PostMapping("/asaas")
    public ResponseEntity<Void> receber(
            @RequestHeader(value = "asaas-access-token", required = false) String tokenRecebido,
            @RequestBody AsaasWebhookRequest payload) {
        if (!WebhookTokenHelper.valido(tokenEsperado, tokenRecebido)) {
            log.warn("webhook Asaas recusado: token ausente ou inválido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("webhook Asaas recebido: evento={} pagamentoId={} status={} externalReference={}",
                payload.event(), idDoPagamento(payload), statusDoPagamento(payload), referenciaDoPagamento(payload));
        return ResponseEntity.ok().build();
    }

    private static String idDoPagamento(AsaasWebhookRequest payload) {
        return payload.payment() == null ? null : payload.payment().id();
    }

    private static String statusDoPagamento(AsaasWebhookRequest payload) {
        return payload.payment() == null ? null : payload.payment().status();
    }

    private static String referenciaDoPagamento(AsaasWebhookRequest payload) {
        return payload.payment() == null ? null : payload.payment().externalReference();
    }
}
