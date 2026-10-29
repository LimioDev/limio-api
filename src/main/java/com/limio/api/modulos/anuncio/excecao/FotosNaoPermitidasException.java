package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Anexar fotos a anúncio que já não está {@code PUBLICADO} ou que já tem prestador escolhido
 * (UC "Anexar fotos ao anúncio").
 */
public class FotosNaoPermitidasException extends NegocioException {

    public FotosNaoPermitidasException() {
        super(CodigoErro.ANUNCIO_ESTADO_INVALIDO, HttpStatus.CONFLICT,
                "Não é mais possível anexar fotos a este anúncio.");
    }
}
