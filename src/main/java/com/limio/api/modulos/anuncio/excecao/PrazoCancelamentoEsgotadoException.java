package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * O Empregador tentou cancelar o serviço combinado com menos de 24 horas para a data/hora vigente (ETI-94, RB08).
 * Nada é alterado e nenhuma cota é estornada.
 */
public class PrazoCancelamentoEsgotadoException extends NegocioException {

    public PrazoCancelamentoEsgotadoException() {
        super(CodigoErro.PRAZO_CANCELAMENTO_ESGOTADO, HttpStatus.CONFLICT,
                CodigoErro.PRAZO_CANCELAMENTO_ESGOTADO.mensagemPadrao());
    }
}
