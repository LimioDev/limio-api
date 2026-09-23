package com.limio.api.comum.records;

import java.time.Instant;

import com.limio.api.comum.excecao.enums.CodigoErro;

public record ErroResponse(CodigoErro codigo, String mensagem, Instant timestamp, String path) {

    public static ErroResponse de(CodigoErro codigo, String mensagem, String path) {
        return new ErroResponse(codigo, mensagem, Instant.now(), path);
    }
}
