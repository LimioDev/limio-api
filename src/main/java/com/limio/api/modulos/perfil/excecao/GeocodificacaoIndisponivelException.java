package com.limio.api.modulos.perfil.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * Serviço de geocodificação fora do ar, lento demais ou recusando a consulta. O perfil não é salvo: sem
 * coordenadas o mural não consegue calcular distância, e salvar o município sem elas deixaria o mural do
 * Prestador vazio sem aviso.
 */
public class GeocodificacaoIndisponivelException extends NegocioException {

    public GeocodificacaoIndisponivelException() {
        super(CodigoErro.GEOCODIFICACAO_INDISPONIVEL, HttpStatus.SERVICE_UNAVAILABLE,
                CodigoErro.GEOCODIFICACAO_INDISPONIVEL.mensagemPadrao());
    }
}
