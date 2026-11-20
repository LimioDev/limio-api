package com.limio.api.modulos.financeiro.records;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code gatewayToken} é o token devolvido pelo gateway na tokenização do cartão — nunca número nem CVV.
 * Opcional: sem ele, a cobrança usa o cartão salvo do Prestador ({@code MeioPagamento}), que é o caso do
 * app (ele só conhece bandeira e últimos 4).
 */
public record AssinarGoldRequest(
        @NotBlank(message = "{assinarGold.codigoPlano.obrigatorio}")
        String codigoPlano,

        String gatewayToken) {
}
