package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Serviço já em andamento ou aguardando validação não é cancelado pelo Empregador: o caminho é a denúncia (ETI-94,
 * RB08/UC42). O código orienta o cliente, como {@link UseCancelamentoServicoException}.
 */
public class UseDenunciaException extends NegocioException {

    public UseDenunciaException() {
        super(CodigoErro.USE_DENUNCIA, HttpStatus.CONFLICT, CodigoErro.USE_DENUNCIA.mensagemPadrao());
    }
}
