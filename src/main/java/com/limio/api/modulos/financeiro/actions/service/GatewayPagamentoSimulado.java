package com.limio.api.modulos.financeiro.actions.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.limio.api.modulos.financeiro.excecao.GatewayIndisponivelException;

/**
 * Simulação do gateway de pagamento (ETI-81), ativa por padrão ({@code financeiro.gateway.provedor},
 * padrão {@code simulado}) — dev local e testes nunca dependem de rede nem de credencial real.
 * Determinístico por token pra sustentar os testes de story (aprovado/recusado/indisponível):
 *
 * <ul>
 *   <li>{@code financeiro.gateway.token-recusado} (padrão {@code tok_recusado}) recusa a cobrança;</li>
 *   <li>{@code financeiro.gateway.token-indisponivel} (padrão {@code tok_indisponivel}) simula o gateway fora do ar;</li>
 *   <li>qualquer outro token aprova.</li>
 * </ul>
 *
 * {@code bandeira} fixa em {@code VISA} e {@code ultimos4} derivado do hash do token: só pra ter algo
 * estável pra exibir, sem significado real. Em Railway, {@code financeiro.gateway.provedor=asaas} troca
 * pra {@link GatewayPagamentoAsaas}.
 */
@Component
@ConditionalOnProperty(name = "financeiro.gateway.provedor", havingValue = "simulado", matchIfMissing = true)
public class GatewayPagamentoSimulado implements GatewayPagamento {

    private final String tokenRecusado;
    private final String tokenIndisponivel;

    public GatewayPagamentoSimulado(
            @Value("${financeiro.gateway.token-recusado:tok_recusado}") String tokenRecusado,
            @Value("${financeiro.gateway.token-indisponivel:tok_indisponivel}") String tokenIndisponivel) {
        this.tokenRecusado = tokenRecusado;
        this.tokenIndisponivel = tokenIndisponivel;
    }

    @Override
    public ResultadoCobranca cobrar(UUID usuarioId, String gatewayToken, long valorCentavos, String moeda,
            String chaveIdempotencia) {
        if (tokenIndisponivel.equals(gatewayToken)) {
            throw new GatewayIndisponivelException();
        }
        if (tokenRecusado.equals(gatewayToken)) {
            return ResultadoCobranca.recusada();
        }
        String ultimos4 = String.format("%04d", Math.abs(gatewayToken.hashCode()) % 10_000);
        return new ResultadoCobranca(true, "SIM-" + UUID.randomUUID(), "VISA", ultimos4);
    }
}
