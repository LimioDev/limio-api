package com.limio.api.modulos.financeiro.actions.service.asaas;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.limio.api.modulos.financeiro.actions.service.DadosCartao;
import com.limio.api.modulos.financeiro.actions.service.GatewayPagamento;
import com.limio.api.modulos.financeiro.actions.service.ResultadoCobranca;
import com.limio.api.modulos.financeiro.actions.service.ResultadoTokenizacao;
import com.limio.api.modulos.financeiro.actions.service.TitularCartao;

/**
 * Gateway real via Asaas (ETI-81), ativo quando {@code financeiro.gateway.provedor=asaas} (Railway
 * sandbox/produção — ver {@code application.properties}). {@code asaas.base-url} escolhe o ambiente
 * Asaas (sandbox ou produção); nunca hardcoded aqui.
 *
 * <p>{@code gatewayToken} precisa já ser um {@code creditCardToken} válido do Asaas, tokenizado pelo
 * mesmo customer que {@link ClienteAsaasResolver} resolve aqui — ver {@code TokenizarCartaoUseCase}
 * (endpoint {@code POST /financeiro/gold/cartao}), que tokeniza contra esse mesmo customer.
 */
@Component
@ConditionalOnProperty(name = "financeiro.gateway.provedor", havingValue = "asaas")
public class GatewayPagamentoAsaas implements GatewayPagamento {

    private final AsaasClient asaasClient;
    private final ClienteAsaasResolver clienteAsaasResolver;

    public GatewayPagamentoAsaas(AsaasClient asaasClient, ClienteAsaasResolver clienteAsaasResolver) {
        this.asaasClient = asaasClient;
        this.clienteAsaasResolver = clienteAsaasResolver;
    }

    @Override
    public ResultadoCobranca cobrar(UUID usuarioId, String gatewayToken, long valorCentavos, String moeda,
            String chaveIdempotencia) {
        String customerId = clienteAsaasResolver.resolverOuCriar(usuarioId);

        AsaasClient.CobrancaResposta resposta = asaasClient.cobrar(customerId, gatewayToken, valorCentavos,
                chaveIdempotencia);
        if (!resposta.aprovada()) {
            return ResultadoCobranca.recusada();
        }
        String bandeira = resposta.creditCard() == null ? null : resposta.creditCard().creditCardBrand();
        String ultimos4 = resposta.creditCard() == null ? null : resposta.creditCard().ultimos4();
        return new ResultadoCobranca(true, resposta.id(), bandeira, ultimos4);
    }

    @Override
    public ResultadoTokenizacao tokenizar(UUID usuarioId, DadosCartao cartao, TitularCartao titular,
            String remoteIp) {
        String customerId = clienteAsaasResolver.resolverOuCriar(usuarioId);
        AsaasClient.TokenizarResposta resposta = asaasClient.tokenizarCartao(customerId, cartao.holderName(),
                cartao.numero(), cartao.expiryMonth(), cartao.expiryYear(), cartao.ccv(), titular.nome(),
                titular.email(), titular.cpf(), titular.cep(), titular.numeroEndereco(), titular.telefone(),
                remoteIp);
        return new ResultadoTokenizacao(resposta.creditCardToken(), resposta.creditCardBrand(), resposta.ultimos4());
    }
}
