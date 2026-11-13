package com.limio.api.modulos.financeiro.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Já existe uma {@code Cobranca} {@code PENDENTE} pra este usuário (ETI-81) — uma tentativa anterior
 * reservou a assinatura e chamou o gateway, mas a confirmação ainda não chegou (em andamento ou perdida
 * por falha entre a cobrança e a segunda transação). Nova tentativa é recusada aqui em vez de chamar o
 * gateway de novo; ver {@code AssinarGoldUseCase} e a pendência de reconciliação via webhook.
 */
public class CobrancaEmAndamentoException extends NegocioException {

    public CobrancaEmAndamentoException() {
        super(CodigoErro.COBRANCA_EM_ANDAMENTO, HttpStatus.CONFLICT, CodigoErro.COBRANCA_EM_ANDAMENTO.mensagemPadrao());
    }
}
