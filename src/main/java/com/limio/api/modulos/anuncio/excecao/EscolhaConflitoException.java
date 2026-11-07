package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Outro candidato já foi escolhido para o anúncio, antes desta escolha (ETI-69).*/
public class EscolhaConflitoException extends NegocioException {

    public EscolhaConflitoException() {
        super(CodigoErro.ESCOLHA_CONFLITO, HttpStatus.CONFLICT, CodigoErro.ESCOLHA_CONFLITO.mensagemPadrao());
    }
}
