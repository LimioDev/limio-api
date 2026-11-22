package com.limio.api.modulos.moderacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Pedido de decisão que não fecha entre os campos (ETI-116): improcedente com sanção, procedente sem sanção, suspensão
 * sem período ou com o fim antes do início, avaliação que não é do denunciado. Responde 400 {@code VALIDACAO_FALHOU},
 * como a validação de campo (o card sugeria {@code DADOS_INVALIDOS}).
 */
public class DecisaoInvalidaException extends NegocioException {

    public DecisaoInvalidaException(String mensagem) {
        super(CodigoErro.VALIDACAO_FALHOU, HttpStatus.BAD_REQUEST, mensagem);
    }
}
