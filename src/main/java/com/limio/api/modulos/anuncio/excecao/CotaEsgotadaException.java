package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Prestador FREE já usou as {@code max-escolhas-gratuitas} escolhas do mês (ETI-75). O candidatar-se em
 * si não consome cota — só a escolha consome (UC33, card futuro) — mas bloqueia aqui pra não deixar o
 * Prestador se candidatar a algo que, escolhido, ele não teria como aceitar.
 */
public class CotaEsgotadaException extends NegocioException {

    public CotaEsgotadaException() {
        super(CodigoErro.COTA_ESGOTADA, HttpStatus.FORBIDDEN, CodigoErro.COTA_ESGOTADA.mensagemPadrao());
    }
}
