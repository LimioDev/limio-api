package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Papel ativo diferente de {@code PRESTADOR} consultando a cota do mês (US24). Mesmo código das outras rotas
 * presas a um papel (403 {@code ACESSO_NEGADO}).
 */
public class SomentePrestadorConsultaCotaException extends NegocioException {

    public SomentePrestadorConsultaCotaException() {
        super(CodigoErro.ACESSO_NEGADO, HttpStatus.FORBIDDEN, "Só o Prestador tem cota de escolhas.");
    }
}
