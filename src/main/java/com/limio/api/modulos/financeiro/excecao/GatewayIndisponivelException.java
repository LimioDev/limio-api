package com.limio.api.modulos.financeiro.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Gateway de pagamento fora do ar ou sem resposta (ETI-81) — mesmo padrão 503 de outras integrações externas. */
public class GatewayIndisponivelException extends NegocioException {

    public GatewayIndisponivelException() {
        super(CodigoErro.GATEWAY_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE, CodigoErro.GATEWAY_INDISPONIVEL.mensagemPadrao());
    }
}
