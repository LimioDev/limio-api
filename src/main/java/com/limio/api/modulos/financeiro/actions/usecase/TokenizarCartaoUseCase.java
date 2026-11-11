package com.limio.api.modulos.financeiro.actions.usecase;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.financeiro.actions.service.asaas.AsaasClient;
import com.limio.api.modulos.financeiro.actions.service.asaas.ClienteAsaasResolver;
import com.limio.api.modulos.financeiro.excecao.SomentePrestadorAssinaGoldException;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamento;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoService;

/**
 * UC — tokeniza o cartão no Asaas e salva como meio de pagamento do Prestador (ETI-81, integração
 * Asaas). Só existe com {@code financeiro.gateway.provedor=asaas}: o gateway simulado não tem conceito
 * de tokenização — nos testes/dev local, qualquer string já serve de {@code gatewayToken} em
 * {@code AssinarGoldRequest}.
 *
 * <p>Dados do cartão (número, validade, CVV) passam só por aqui e pelo {@link AsaasClient} — nunca
 * chegam a {@code MeioPagamento} nem a log algum; só o token e os dados de exibição (bandeira,
 * últimos 4) são persistidos.
 */
@Service
@ConditionalOnProperty(name = "financeiro.gateway.provedor", havingValue = "asaas")
public class TokenizarCartaoUseCase {

    private final ClienteAsaasResolver clienteAsaasResolver;
    private final AsaasClient asaasClient;
    private final MeioPagamentoService meioPagamentoService;

    public TokenizarCartaoUseCase(ClienteAsaasResolver clienteAsaasResolver, AsaasClient asaasClient,
            MeioPagamentoService meioPagamentoService) {
        this.clienteAsaasResolver = clienteAsaasResolver;
        this.asaasClient = asaasClient;
        this.meioPagamentoService = meioPagamentoService;
    }

    public MeioPagamento executar(UUID usuarioId, PapelUsuario papelAtivo, String holderName, String numero,
            String expiryMonth, String expiryYear, String ccv, String holderNome, String holderEmail,
            String holderCpf, String holderCep, String holderNumeroEndereco, String holderTelefone,
            String remoteIp) {
        exigirPrestador(papelAtivo);

        String customerId = clienteAsaasResolver.resolverOuCriar(usuarioId);
        AsaasClient.TokenizarResposta resposta = asaasClient.tokenizarCartao(customerId, holderName, numero,
                expiryMonth, expiryYear, ccv, holderNome, holderEmail, holderCpf, holderCep, holderNumeroEndereco,
                holderTelefone, remoteIp);

        return meioPagamentoService.salvarOuSubstituir(usuarioId, resposta.creditCardToken(),
                resposta.creditCardBrand(), resposta.ultimos4());
    }

    private static void exigirPrestador(PapelUsuario papelAtivo) {
        switch (papelAtivo) {
            case PRESTADOR -> {
            }
            case EMPREGADOR -> throw new SomentePrestadorAssinaGoldException();
        }
    }
}
