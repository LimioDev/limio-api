package com.limio.api.modulos.financeiro.actions.service;

import java.util.UUID;

/**
 * Porta de integração com o gateway de pagamento (ETI-81): os usecases dependem desta interface, nunca
 * do provedor concreto. Fica em {@code financeiro} e não em {@code comum/portas} porque só este módulo
 * cobra (critério "2+ módulos hoje" do ADR-0001). Provedor ativo escolhido por
 * {@code financeiro.gateway.provedor} (padrão {@code simulado}; {@code asaas} em Railway).
 */
public interface GatewayPagamento {

    /**
     * Cobra {@code valorCentavos} no cartão representado por {@code gatewayToken}, do usuário
     * {@code usuarioId} (alguns provedores, como o Asaas, precisam de um cliente cadastrado do lado
     * deles). {@code chaveIdempotencia} viaja pro gateway pra ele também recusar uma segunda cobrança da
     * mesma tentativa, se o LIMIO precisar repetir a chamada (timeout, retry).
     *
     * @throws com.limio.api.modulos.financeiro.excecao.GatewayIndisponivelException gateway fora do ar ou sem resposta
     */
    ResultadoCobranca cobrar(UUID usuarioId, String gatewayToken, long valorCentavos, String moeda,
            String chaveIdempotencia);

    /**
     * Tokeniza o cartão do usuário {@code usuarioId} e devolve um {@code gatewayToken} que
     * {@link #cobrar} aceita depois. Dados crus do cartão passam só por aqui e pelo provedor concreto —
     * nunca são persistidos. {@code remoteIp} é o IP de quem está cadastrando o cartão (antifraude).
     *
     * @throws com.limio.api.modulos.financeiro.excecao.CartaoInvalidoException dados do cartão recusados
     * @throws com.limio.api.modulos.financeiro.excecao.GatewayIndisponivelException gateway fora do ar ou sem resposta
     */
    ResultadoTokenizacao tokenizar(UUID usuarioId, DadosCartao cartao, TitularCartao titular, String remoteIp);
}
