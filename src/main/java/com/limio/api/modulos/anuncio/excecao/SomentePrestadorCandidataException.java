package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Papel ativo diferente de {@code PRESTADOR} tentando se candidatar (ETI-75). Mesmo código de {@link
 * SomenteEmpregadorPublicaException} e das rotas de endereço salvo (403 {@code ACESSO_NEGADO}).
 */
public class SomentePrestadorCandidataException extends NegocioException {

    public SomentePrestadorCandidataException() {
        super(CodigoErro.ACESSO_NEGADO, HttpStatus.FORBIDDEN, "Troque para o papel de Prestador para se candidatar.");
    }
}
