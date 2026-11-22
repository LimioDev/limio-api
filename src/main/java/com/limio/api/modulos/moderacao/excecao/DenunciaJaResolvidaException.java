package com.limio.api.modulos.moderacao.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/** A denúncia já tem decisão: outro administrador decidiu antes, ou o painel mandou de novo (ETI-116). */
public class DenunciaJaResolvidaException extends NegocioException {

    public DenunciaJaResolvidaException() {
        super(CodigoErro.DENUNCIA_JA_RESOLVIDA, HttpStatus.CONFLICT, CodigoErro.DENUNCIA_JA_RESOLVIDA.mensagemPadrao());
    }
}
