package com.limio.api.modulos.moderacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Avaliação sem nota e sem comentário. Fica no usecase, não no {@code @NotNull} do record: com comentário o código é
 * outro ({@code COMENTARIO_SEM_NOTA}), e as duas regras ficam juntas.
 */
public class NotaObrigatoriaException extends NegocioException {

    private static final String MENSAGEM = "Escolha uma nota de 1 a 5.";

    public NotaObrigatoriaException() {
        super(CodigoErro.VALIDACAO_FALHOU, HttpStatus.BAD_REQUEST, MENSAGEM);
    }
}
