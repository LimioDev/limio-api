package com.limio.api.comum.midia.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class FotoInvalidaException extends NegocioException {

    private FotoInvalidaException(String mensagem) {
        super(CodigoErro.FOTO_INVALIDA, HttpStatus.UNPROCESSABLE_ENTITY, mensagem);
    }

    public static FotoInvalidaException vazia() {
        return new FotoInvalidaException("Selecione uma foto para enviar.");
    }

    public static FotoInvalidaException grandeDemais(long limiteBytes) {
        return new FotoInvalidaException("A foto deve ter no máximo " + (limiteBytes / (1024 * 1024)) + " MB.");
    }

    public static FotoInvalidaException formatoNaoAceito() {
        return new FotoInvalidaException(CodigoErro.FOTO_INVALIDA.mensagemPadrao());
    }

    /** Fotos de anúncio (ETI-59): as já gravadas mais as enviadas agora passariam do limite. */
    public static FotoInvalidaException quantidadeExcedida(int maximo) {
        return new FotoInvalidaException("O anúncio pode ter no máximo " + maximo + " fotos.");
    }
}
