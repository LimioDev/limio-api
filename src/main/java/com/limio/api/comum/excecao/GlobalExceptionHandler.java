package com.limio.api.comum.excecao;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
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

    /** JSON malformado ou valor fora de um enum (ex.: papel inexistente) — erro do cliente, não 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> tratarCorpoIlegivel(HttpMessageNotReadableException ex, WebRequest request) {
        ErroResponse corpo = ErroResponse.de(CodigoErro.VALIDACAO_FALHOU, CodigoErro.VALIDACAO_FALHOU.mensagemPadrao(), caminho(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo);
    }

    /**
     * Rota protegida sem token de acesso válido. Chega aqui pelo entry point do
     * {@code SecurityConfig}, que delega ao {@code HandlerExceptionResolver}.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResponse> tratarNaoAutenticado(AuthenticationException ex, WebRequest request) {
        ErroResponse corpo = ErroResponse.de(CodigoErro.NAO_AUTENTICADO, CodigoErro.NAO_AUTENTICADO.mensagemPadrao(), caminho(request));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(corpo);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> tratarInesperado(Exception ex, WebRequest request) {
        // Erro de cliente detectado pelo próprio Spring (Content-Type não suportado, método errado, rota inexistente):
        // mantém o status 4xx em vez de virar 500.
        if (ex instanceof ErrorResponse erroDoFramework && erroDoFramework.getStatusCode().is4xxClientError()) {
            ErroResponse corpo = ErroResponse.de(CodigoErro.REQUISICAO_INVALIDA, CodigoErro.REQUISICAO_INVALIDA.mensagemPadrao(), caminho(request));
            return ResponseEntity.status(erroDoFramework.getStatusCode()).body(corpo);
        }
        ErroResponse corpo = ErroResponse.de(CodigoErro.ERRO_INTERNO, CodigoErro.ERRO_INTERNO.mensagemPadrao(), caminho(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpo);
    }

    private String caminho(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
