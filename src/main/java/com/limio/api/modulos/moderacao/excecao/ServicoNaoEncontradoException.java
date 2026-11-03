package com.limio.api.modulos.moderacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Serviço inexistente, ou de quem não é uma das partes: as duas situações respondem igual, pra não revelar que o serviço existe. */
public class ServicoNaoEncontradoException extends NegocioException {

    public ServicoNaoEncontradoException() {
        super(CodigoErro.SERVICO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND, CodigoErro.SERVICO_NAO_ENCONTRADO.mensagemPadrao());
    }
}
