package com.limio.api.comum.excecao;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.limio.api.comum.excecao.enums.CodigoErro;
import com.limio.api.comum.records.ErroResponse;

/**
 * Único ponto que traduz exception de negócio (ou de validação) em resposta
 * HTTP. Controller de módulo nunca escreve try/catch pra isso.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErroResponse> tratarNegocio(NegocioException ex, WebRequest request) {
        ErroResponse corpo = ErroResponse.de(ex.getCodigo(), ex.getMessage(), caminho(request));
        return ResponseEntity.status(ex.getStatus()).body(corpo);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> tratarValidacao(MethodArgumentNotValidException ex, WebRequest request) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErroResponse corpo = ErroResponse.de(CodigoErro.VALIDACAO_FALHOU, mensagem, caminho(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> tratarInesperado(Exception ex, WebRequest request) {
        ErroResponse corpo = ErroResponse.de(CodigoErro.ERRO_INTERNO, CodigoErro.ERRO_INTERNO.mensagemPadrao(), caminho(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpo);
    }

    private String caminho(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
