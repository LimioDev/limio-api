package com.limio.api.modulos.financeiro.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Gateway recusou a cobrança (ETI-81): nada é persistido, o Prestador permanece FREE. */
public class PagamentoRecusadoException extends NegocioException {

    public PagamentoRecusadoException() {
        super(CodigoErro.PAGAMENTO_RECUSADO, HttpStatus.PAYMENT_REQUIRED, CodigoErro.PAGAMENTO_RECUSADO.mensagemPadrao());
    }
}
