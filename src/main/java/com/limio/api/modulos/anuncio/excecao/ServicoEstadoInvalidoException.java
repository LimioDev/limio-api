package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Serviço fora de {@code PUBLICADO}: já iniciado, em validação, concluído ou encerrado (ETI-88). */
public class ServicoEstadoInvalidoException extends NegocioException {

    public ServicoEstadoInvalidoException() {
        super(CodigoErro.SERVICO_ESTADO_INVALIDO, HttpStatus.CONFLICT,
                CodigoErro.SERVICO_ESTADO_INVALIDO.mensagemPadrao());
    }
}
