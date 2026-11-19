package com.limio.api.modulos.anuncio.excecao;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * A categoria de um anúncio já publicado não existe mais (ETI-65). Só acontece no aviso aos Prestadores, que roda
 * depois do commit da publicação: não chega a virar resposta HTTP, fica no log com o id do anúncio.
 */
public class CategoriaDoAnuncioNaoEncontradaException extends NegocioException {

    public CategoriaDoAnuncioNaoEncontradaException(UUID anuncioId) {
        super(CodigoErro.CATEGORIA_INVALIDA, HttpStatus.UNPROCESSABLE_ENTITY,
                "A categoria do anúncio " + anuncioId + " não existe.");
    }
}
