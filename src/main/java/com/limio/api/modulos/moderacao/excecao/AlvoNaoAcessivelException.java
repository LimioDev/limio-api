package com.limio.api.modulos.moderacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Alvo da denúncia que não existe, que o denunciante não acessa (serviço ou chat de que não é parte) ou que é ele
 * mesmo (ETI-114). Todas as situações respondem igual, pra não revelar o que existe.
 */
public class AlvoNaoAcessivelException extends NegocioException {

    public AlvoNaoAcessivelException() {
        super(CodigoErro.ALVO_NAO_ACESSIVEL, HttpStatus.NOT_FOUND, CodigoErro.ALVO_NAO_ACESSIVEL.mensagemPadrao());
    }
}
