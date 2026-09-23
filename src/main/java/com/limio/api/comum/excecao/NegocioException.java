package com.limio.api.comum.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.enums.CodigoErro;

import lombok.Getter;

/**
 * Classe base de toda exceção de regra de negócio. Módulos estendem esta
 * classe em vez de lançar RuntimeException solta — {@link GlobalExceptionHandler}
 * é o único ponto que traduz isso pra HTTP.
 */
@Getter
public abstract class NegocioException extends RuntimeException {

    private final CodigoErro codigo;
    private final HttpStatus status;

    protected NegocioException(CodigoErro codigo, HttpStatus status, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
        this.status = status;
    }
}
