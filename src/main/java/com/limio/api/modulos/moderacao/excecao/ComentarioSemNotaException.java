package com.limio.api.modulos.moderacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Comentário enviado sem nota: o comentário só acompanha uma nota. */
public class ComentarioSemNotaException extends NegocioException {

    public ComentarioSemNotaException() {
        super(CodigoErro.COMENTARIO_SEM_NOTA, HttpStatus.BAD_REQUEST, CodigoErro.COMENTARIO_SEM_NOTA.mensagemPadrao());
    }
}
