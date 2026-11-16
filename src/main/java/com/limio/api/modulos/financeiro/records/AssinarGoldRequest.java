package com.limio.api.modulos.financeiro.records;

import jakarta.validation.constraints.NotBlank;

/** {@code gatewayToken} é o token devolvido pelo gateway na tokenização do cartão — nunca número nem CVV. */
public record AssinarGoldRequest(
        @NotBlank(message = "{assinarGold.codigoPlano.obrigatorio}")
        String codigoPlano,

        @NotBlank(message = "{assinarGold.gatewayToken.obrigatorio}")
        String gatewayToken) {
}
