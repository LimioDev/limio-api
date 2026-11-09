package com.limio.api.modulos.moderacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Só se avalia com o anúncio {@code CONCLUIDO}, por validação ou por silêncio. */
public class ServicoNaoConcluidoException extends NegocioException {

    public ServicoNaoConcluidoException() {
        super(CodigoErro.SERVICO_NAO_CONCLUIDO, HttpStatus.CONFLICT, CodigoErro.SERVICO_NAO_CONCLUIDO.mensagemPadrao());
    }
}
