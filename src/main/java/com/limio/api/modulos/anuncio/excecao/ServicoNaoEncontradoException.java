package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Anúncio inexistente, sem candidatura escolhida, ou de quem não é uma das duas partes do combinado — as três
 * situações respondem igual, pra não revelar se o anúncio existe (ETI-86).
 *
 * <p>Mesmo código da PR#26 (ETI-88, "iniciar o serviço"): as duas features tratam {@code Anuncio} como "o
 * serviço" do ponto de vista pós-escolha. Se a #26 mergear primeiro, apagar esta classe e reusar a dela.
 */
public class ServicoNaoEncontradoException extends NegocioException {

    public ServicoNaoEncontradoException() {
        super(CodigoErro.SERVICO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND, CodigoErro.SERVICO_NAO_ENCONTRADO.mensagemPadrao());
    }
}
