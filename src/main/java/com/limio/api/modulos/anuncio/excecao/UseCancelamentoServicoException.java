package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Prestador já escolhido não desiste da candidatura: cancela o serviço combinado (ETI-96/ETI-97), conforme o
 * UC (ETI-77).
 */
public class UseCancelamentoServicoException extends NegocioException {

    public UseCancelamentoServicoException() {
        super(CodigoErro.USE_CANCELAMENTO_SERVICO, HttpStatus.CONFLICT,
                CodigoErro.USE_CANCELAMENTO_SERVICO.mensagemPadrao());
    }
}
