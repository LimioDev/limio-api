package com.limio.api.modulos.financeiro.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Gateway de pagamento fora do ar ou sem resposta (ETI-81) — mesmo padrão 503 de outras integrações externas.
 *
 * <p>{@link #cobrancaPodeTerOcorrido()} separa os dois casos que o {@code AssinarGoldUseCase} trata de
 * jeitos diferentes: com a requisição comprovadamente não processada (conexão recusada, chave inválida),
 * a pendência pode ser desfeita e o Prestador tenta de novo; com o resultado incerto (timeout de leitura,
 * 5xx, resposta sem id), o gateway pode ter cobrado, e a pendência fica até a reconciliação.
 */
public class GatewayIndisponivelException extends NegocioException {

    private final boolean cobrancaPodeTerOcorrido;

    /** O gateway não processou a requisição: nada foi cobrado. */
    public GatewayIndisponivelException() {
        this(false);
    }

    private GatewayIndisponivelException(boolean cobrancaPodeTerOcorrido) {
        super(CodigoErro.GATEWAY_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE, CodigoErro.GATEWAY_INDISPONIVEL.mensagemPadrao());
        this.cobrancaPodeTerOcorrido = cobrancaPodeTerOcorrido;
    }

    /** A requisição chegou ao gateway, mas a resposta se perdeu ou veio inválida: a cobrança pode ter ocorrido. */
    public static GatewayIndisponivelException resultadoIncerto() {
        return new GatewayIndisponivelException(true);
    }

    public boolean cobrancaPodeTerOcorrido() {
        return cobrancaPodeTerOcorrido;
    }
}
