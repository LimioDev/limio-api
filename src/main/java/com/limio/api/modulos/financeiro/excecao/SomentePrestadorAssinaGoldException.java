package com.limio.api.modulos.financeiro.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** Papel ativo diferente de {@code PRESTADOR} tentando consultar ou assinar o GOLD (ETI-81). */
public class SomentePrestadorAssinaGoldException extends NegocioException {

    public SomentePrestadorAssinaGoldException() {
        super(CodigoErro.ACESSO_NEGADO, HttpStatus.FORBIDDEN, "Troque para o papel de Prestador para assinar o GOLD.");
    }
}
