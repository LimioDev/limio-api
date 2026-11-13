package com.limio.api.modulos.financeiro.actions.service.asaas;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.limio.api.modulos.financeiro.excecao.CartaoInvalidoException;
import com.limio.api.modulos.financeiro.excecao.GatewayIndisponivelException;

/**
 * Cliente HTTP do Asaas (ETI-81) — {@code asaas.base-url} escolhe sandbox ou produção, nunca
 * hardcoded; ver {@code application.properties}. Autenticação por header {@code access_token} (padrão
 * do Asaas, não é Bearer).
 *
 * <p>Erro 401/403 (chave inválida/sem permissão) e 5xx/timeout são {@link GatewayIndisponivelException}
 * — problema de configuração ou do provedor, não do cartão. Qualquer outro 4xx na criação da cobrança é
 * tratado como recusa do cartão: é o caso dominante desse endpoint quando customer e token já são
 * válidos. Ajustar se o sandbox revelar um código de erro do Asaas que precise de tratamento próprio.
 */
@Component
@ConditionalOnProperty(name = "financeiro.gateway.provedor", havingValue = "asaas")
public class AsaasClient {

    private static final Logger log = LoggerFactory.getLogger(AsaasClient.class);
    private static final String BILLING_TYPE_CARTAO = "CREDIT_CARD";

    private final RestClient restClient;

    public AsaasClient(@Value("${asaas.base-url}") String baseUrl, @Value("${asaas.api-key}") String apiKey,
            @Value("${asaas.timeout:10s}") Duration timeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("access_token", apiKey)
                .defaultHeader(HttpHeaders.USER_AGENT, "limio-api")
                .requestFactory(requestFactory)
                .build();
    }

    /** Cria o customer no Asaas. {@code externalReference} é o usuarioId, pra rastrear do lado do Asaas. */
    public String criarCliente(String nome, String cpf, String externalReference) {
        try {
            ClienteResposta resposta = restClient.post()
                    .uri("/customers")
                    .body(new ClienteRequest(nome, cpf, externalReference))
                    .retrieve()
                    .body(ClienteResposta.class);
            if (resposta == null || resposta.id() == null) {
                throw respostaInvalida("customers");
            }
            return resposta.id();
        } catch (RestClientResponseException e) {
            throw erroDeConfiguracaoOuIndisponivel(e, "customers");
        } catch (RestClientException e) {
            log.warn("Asaas indisponível ao criar customer: {}", e.getMessage());
            throw new GatewayIndisponivelException();
        }
    }

    /**
     * Cobra {@code valorCentavos} no cartão já tokenizado. {@code externalReference} é a chave de
     * idempotência: o Asaas recusa uma segunda cobrança com a mesma chave pro mesmo customer.
     */
    public CobrancaResposta cobrar(String customerId, String creditCardToken, long valorCentavos,
            String externalReference) {
        double valor = valorCentavos / 100.0;
        String vencimento = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            CobrancaResposta resposta = restClient.post()
                    .uri("/payments")
                    .body(new CobrancaRequest(customerId, BILLING_TYPE_CARTAO, valor, vencimento, creditCardToken,
                            externalReference))
                    .retrieve()
                    .body(CobrancaResposta.class);
            if (resposta == null || resposta.id() == null) {
                throw respostaInvalida("payments");
            }
            return resposta;
        } catch (RestClientResponseException e) {
            if (configuracaoInvalida(e.getStatusCode())) {
                throw erroDeConfiguracaoOuIndisponivel(e, "payments");
            }
            // 4xx que não é erro de chave/permissão: recusa do cartão (ex.: saldo insuficiente, cartão
            // expirado) — o chamador decide o que fazer com isso.
            log.info("Asaas recusou a cobrança (status {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            return CobrancaResposta.recusada();
        } catch (RestClientException e) {
            log.warn("Asaas indisponível ao cobrar: {}", e.getMessage());
            throw new GatewayIndisponivelException();
        }
    }

    /**
     * Tokeniza o cartão — único lugar do LIMIO que toca número/CVV, e só pra repassar ao Asaas, nunca pra
     * persistir (TokenizarCartaoUseCase descarta tudo menos o token devolvido). {@code remoteIp} é o IP de
     * quem está cadastrando o cartão (exigência do Asaas, antifraude).
     */
    public TokenizarResposta tokenizarCartao(String customerId, String holderName, String numero,
            String expiryMonth, String expiryYear, String ccv, String holderNome, String holderEmail,
            String holderCpf, String holderCep, String holderNumeroEndereco, String holderTelefone,
            String remoteIp) {
        try {
            TokenizarResposta resposta = restClient.post()
                    .uri("/creditCard/tokenize")
                    .body(new TokenizarRequest(customerId,
                            new CartaoRequest(holderName, numero, expiryMonth, expiryYear, ccv),
                            new HolderInfoRequest(holderNome, holderEmail, holderCpf, holderCep,
                                    holderNumeroEndereco, holderTelefone),
                            remoteIp))
                    .retrieve()
                    .body(TokenizarResposta.class);
            if (resposta == null || resposta.creditCardToken() == null) {
                throw respostaInvalida("creditCard/tokenize");
            }
            return resposta;
        } catch (RestClientResponseException e) {
            if (configuracaoInvalida(e.getStatusCode())) {
                throw erroDeConfiguracaoOuIndisponivel(e, "creditCard/tokenize");
            }
            log.info("Asaas recusou tokenizar o cartão (status {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new CartaoInvalidoException();
        } catch (RestClientException e) {
            log.warn("Asaas indisponível ao tokenizar cartão: {}", e.getMessage());
            throw new GatewayIndisponivelException();
        }
    }

    private static boolean configuracaoInvalida(HttpStatusCode status) {
        return status.value() == 401 || status.value() == 403;
    }

    private GatewayIndisponivelException erroDeConfiguracaoOuIndisponivel(RestClientResponseException e, String recurso) {
        log.error("Asaas respondeu {} em /{} (verifique asaas.api-key/asaas.base-url): {}", e.getStatusCode(),
                recurso, e.getResponseBodyAsString());
        return new GatewayIndisponivelException();
    }

    private GatewayIndisponivelException respostaInvalida(String recurso) {
        log.error("Asaas devolveu 2xx sem id em /{}", recurso);
        return new GatewayIndisponivelException();
    }

    record ClienteRequest(String name, String cpfCnpj, String externalReference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClienteResposta(String id) {
    }

    record CobrancaRequest(String customer, String billingType, double value, String dueDate,
            String creditCardToken, String externalReference) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CobrancaResposta(String id, String status, CartaoResposta creditCard) {

        static CobrancaResposta recusada() {
            return new CobrancaResposta(null, "RECUSADA", null);
        }

        public boolean aprovada() {
            return id != null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CartaoResposta(String creditCardBrand, String creditCardNumber) {

        /** Asaas devolve mascarado, ex.: {@code "****1234"}. */
        public String ultimos4() {
            return creditCardNumber == null ? null : creditCardNumber.substring(Math.max(0, creditCardNumber.length() - 4));
        }
    }

    record CartaoRequest(String holderName, String number, String expiryMonth, String expiryYear, String ccv) {
    }

    record HolderInfoRequest(String name, String email, String cpfCnpj, String postalCode, String addressNumber,
            String phone) {
    }

    record TokenizarRequest(String customer, CartaoRequest creditCard, HolderInfoRequest creditCardHolderInfo,
            String remoteIp) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenizarResposta(String creditCardToken, String creditCardBrand, String creditCardNumber) {

        /** Asaas devolve mascarado, ex.: {@code "****1234"}. */
        public String ultimos4() {
            return creditCardNumber == null ? null : creditCardNumber.substring(Math.max(0, creditCardNumber.length() - 4));
        }
    }
}
