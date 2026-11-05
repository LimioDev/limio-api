package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Anúncio inexistente, sem candidatura escolhida, ou de quem não é uma das duas partes do combinado (ETI-86);
 * também usada por quem não é o Prestador escolhido tentando iniciar o serviço (ETI-88). Todas as situações
 * respondem igual, pra não revelar se o anúncio/serviço existe. Compartilhada entre as duas features, que
 * tratam {@code Anuncio} como "o serviço" do ponto de vista pós-escolha.
 */
public class ServicoNaoEncontradoException extends NegocioException {

    public ServicoNaoEncontradoException() {
        super(CodigoErro.SERVICO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND,
                CodigoErro.SERVICO_NAO_ENCONTRADO.mensagemPadrao());
    }
}
