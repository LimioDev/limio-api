package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Validação "pela foto" só vale a partir de {@code AGUARDANDO_VALIDACAO} (ETI-92): sem o check-out do
 * Prestador (ETI-90), o anúncio nunca chega lá.
 */
public class ValidacaoSemCheckoutException extends NegocioException {

    public ValidacaoSemCheckoutException() {
        super(CodigoErro.VALIDACAO_SEM_CHECKOUT, HttpStatus.CONFLICT, CodigoErro.VALIDACAO_SEM_CHECKOUT.mensagemPadrao());
    }
}
