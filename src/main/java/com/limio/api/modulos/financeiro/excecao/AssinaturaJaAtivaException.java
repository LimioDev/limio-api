package com.limio.api.modulos.financeiro.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Prestador já tem {@code assinatura_gold} com status {@code ATIVA} (ETI-81) — uma segunda assinatura é
 * recusada. Reassinar depois de cancelada (status {@code CANCELADA}, card futuro US26/US27) não cai
 * aqui: reativa a mesma linha em vez de lançar esta exceção.
 */
public class AssinaturaJaAtivaException extends NegocioException {

    public AssinaturaJaAtivaException() {
        super(CodigoErro.ASSINATURA_JA_ATIVA, HttpStatus.CONFLICT, CodigoErro.ASSINATURA_JA_ATIVA.mensagemPadrao());
    }
}
