package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

public class SomentePrestadorVeMuralException extends NegocioException {

    public SomentePrestadorVeMuralException() {
        super(CodigoErro.ACESSO_NEGADO, HttpStatus.FORBIDDEN, "Só o Prestador pode ver o mural de anúncios.");
    }
}
