package com.limio.api.modulos.moderacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Uma avaliação por pessoa por serviço: a segunda é recusada, não substitui a primeira. */
public class AvaliacaoDuplicadaException extends NegocioException {

    public AvaliacaoDuplicadaException() {
        super(CodigoErro.AVALIACAO_DUPLICADA, HttpStatus.CONFLICT, CodigoErro.AVALIACAO_DUPLICADA.mensagemPadrao());
    }
}
