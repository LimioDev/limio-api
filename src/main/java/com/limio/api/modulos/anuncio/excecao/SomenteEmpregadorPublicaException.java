package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Papel ativo diferente de {@code EMPREGADOR} tentando publicar anúncio. Mesmo código das rotas de
 * endereço salvo (403 {@code ACESSO_NEGADO}).
 */
public class SomenteEmpregadorPublicaException extends NegocioException {

    public SomenteEmpregadorPublicaException() {
        super(CodigoErro.ACESSO_NEGADO, HttpStatus.FORBIDDEN,
                "Só o Empregador pode publicar anúncios.");
    }
}
