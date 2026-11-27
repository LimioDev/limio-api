package com.limio.api.modulos.financeiro.actions.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.limio.api.modulos.financeiro.actions.helper.WebhookTokenHelper;
import com.limio.api.modulos.financeiro.actions.mapper.AvisoPagamentoMapper;
import com.limio.api.modulos.financeiro.actions.usecase.ProcessarAvisoPagamentoUseCase;
import com.limio.api.modulos.financeiro.excecao.WebhookInvalidoException;
import com.limio.api.modulos.financeiro.records.AsaasWebhookRequest;

/**
 * Recebe os avisos do Asaas sobre as cobranças (ETI-81, processamento na ETI-85). Rota chamada pelo gateway, não
 * pelo app. Aviso que resolve uma cobrança de cartão vai para {@link ProcessarAvisoPagamentoUseCase}; os demais
 * (estorno, chargeback, criação...) só são logados. Responde 200 nos dois casos, para o Asaas não reenviar.
 *
 * <p>Sem JWT — Asaas não tem o token do LIMIO. Autenticidade é o header {@code asaas-access-token} batendo com
 * {@code asaas.webhook.token} (configurado como "Token de autenticação" no cadastro do webhook no painel Asaas,
 * comparado em tempo constante por {@link WebhookTokenHelper}). Token ausente ou errado: 401 {@code
 * WEBHOOK_INVALIDO}, sem processar. Rota liberada em {@code SecurityConfig}.
 *
 * <p>Só existe quando {@code financeiro.gateway.provedor=asaas} (Railway) — local/teste, com o gateway simulado, não
 * precisa do segredo deste webhook.
 */
@RestController
@RequestMapping("/financeiro/gold/webhook")
@ConditionalOnProperty(name = "financeiro.gateway.provedor", havingValue = "asaas")
public class AsaasWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AsaasWebhookController.class);

    private final String tokenEsperado;
    private final AvisoPagamentoMapper mapper;
    private final ProcessarAvisoPagamentoUseCase processarAviso;

    public AsaasWebhookController(@Value("${asaas.webhook.token}") String tokenEsperado, AvisoPagamentoMapper mapper,
            ProcessarAvisoPagamentoUseCase processarAviso) {
        this.tokenEsperado = tokenEsperado;
        this.mapper = mapper;
        this.processarAviso = processarAviso;
    }

    @PostMapping("/asaas")
    public ResponseEntity<Void> receber(
            @RequestHeader(value = "asaas-access-token", required = false) String tokenRecebido,
            @RequestBody AsaasWebhookRequest payload) {
        if (!WebhookTokenHelper.valido(tokenEsperado, tokenRecebido)) {
            log.warn("webhook Asaas recusado: token ausente ou inválido");
            throw new WebhookInvalidoException();
        }
        log.info("webhook Asaas recebido: evento={} pagamentoId={} status={} externalReference={}",
                payload.event(), idDoPagamento(payload), statusDoPagamento(payload), referenciaDoPagamento(payload));
        mapper.toAviso(payload).ifPresent(processarAviso::executar);
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
