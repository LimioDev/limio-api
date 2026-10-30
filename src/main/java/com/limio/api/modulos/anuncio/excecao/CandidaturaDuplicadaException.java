package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Já existe candidatura ativa (ENVIADA/ESCOLHIDA/AGUARDANDO_VALIDACAO) do mesmo par anúncio+prestador
 * (ETI-75) — tanto pela checagem do usecase quanto pela rede de segurança do índice único
 * {@code uk_aplicacao_par_ativo} ({@link com.limio.api.modulos.anuncio.aplicacao.AplicacaoService#salvarCandidatura}).
 */
public class CandidaturaDuplicadaException extends NegocioException {

    public CandidaturaDuplicadaException() {
        super(CodigoErro.CANDIDATURA_DUPLICADA, HttpStatus.CONFLICT, CodigoErro.CANDIDATURA_DUPLICADA.mensagemPadrao());
    }
}
