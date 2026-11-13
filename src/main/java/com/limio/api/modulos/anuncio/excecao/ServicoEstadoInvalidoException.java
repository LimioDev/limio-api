package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Serviço num estado que não permite a ação: já iniciado, em validação, concluído ou encerrado (ETI-88);
 * check-out fora de {@code PUBLICADO}/{@code EM_ANDAMENTO} (ETI-90).
 */
public class ServicoEstadoInvalidoException extends NegocioException {

    public ServicoEstadoInvalidoException() {
        super(CodigoErro.SERVICO_ESTADO_INVALIDO, HttpStatus.CONFLICT,
                CodigoErro.SERVICO_ESTADO_INVALIDO.mensagemPadrao());
    }
}
