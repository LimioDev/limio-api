package com.limio.api.modulos.moderacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Passaram os 7 dias depois da conclusão do serviço. */
public class JanelaAvaliacaoEncerradaException extends NegocioException {

    public JanelaAvaliacaoEncerradaException() {
        super(CodigoErro.JANELA_AVALIACAO_ENCERRADA, HttpStatus.CONFLICT, CodigoErro.JANELA_AVALIACAO_ENCERRADA.mensagemPadrao());
    }
}
