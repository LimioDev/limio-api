package com.limio.api.modulos.auth.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Anti-enumeração: cobre tanto CPF/e-mail já cadastrados quanto CPF
 * bloqueado por exclusão recente (cooldown de 12 meses). Mensagem é sempre a
 * mesma pro visitante, pra não revelar qual campo colidiu nem o motivo.
 */
public class CadastroIndisponivelException extends NegocioException {

    public CadastroIndisponivelException() {
        super(CodigoErro.CADASTRO_DADOS_INDISPONIVEIS, HttpStatus.CONFLICT,
                CodigoErro.CADASTRO_DADOS_INDISPONIVEIS.mensagemPadrao());
    }
}
