package com.limio.api.modulos.financeiro.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.financeiro.actions.helper.ExigirPrestadorHelper;
import com.limio.api.modulos.financeiro.actions.service.DadosCartao;
import com.limio.api.modulos.financeiro.actions.service.GatewayPagamento;
import com.limio.api.modulos.financeiro.actions.service.ResultadoTokenizacao;
import com.limio.api.modulos.financeiro.actions.service.TitularCartao;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamento;
import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamentoService;

/**
 * UC — tokeniza o cartão no gateway e salva como meio de pagamento do Prestador (ETI-81). Depende só da
 * porta {@link GatewayPagamento}, nunca do provedor concreto — o simulado devolve um token fake, sem rede
 * nem conceito real de tokenização.
 *
 * <p>Dados do cartão (número, validade, CVV) passam só por aqui e pelo {@link GatewayPagamento} — nunca
 * chegam a {@code MeioPagamento} nem a log algum; só o token e os dados de exibição (bandeira,
 * últimos 4) são persistidos.
 */
@Service
public class TokenizarCartaoUseCase {

    private final GatewayPagamento gatewayPagamento;
    private final MeioPagamentoService meioPagamentoService;

    public TokenizarCartaoUseCase(GatewayPagamento gatewayPagamento, MeioPagamentoService meioPagamentoService) {
        this.gatewayPagamento = gatewayPagamento;
        this.meioPagamentoService = meioPagamentoService;
    }

    public MeioPagamento executar(UUID usuarioId, PapelUsuario papelAtivo, DadosCartao cartao, TitularCartao titular,
            String remoteIp) {
        ExigirPrestadorHelper.exigirPrestador(papelAtivo);

        ResultadoTokenizacao resultado = gatewayPagamento.tokenizar(usuarioId, cartao, titular, remoteIp);

        return meioPagamentoService.salvarOuSubstituir(usuarioId, resultado.gatewayToken(), resultado.bandeira(),
                resultado.ultimos4());
    }
}
