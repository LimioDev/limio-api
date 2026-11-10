package com.limio.api.modulos.anuncio.excecao;

import org.springframework.http.HttpStatus;

import com.limio.api.comum.excecao.NegocioException;
import com.limio.api.comum.excecao.enums.CodigoErro;

/**
 * O candidato FREE que o Empregador tentou escolher já usou as escolhas do mês entre a candidatura e agora (ETI-69).
 * Diferente de {@link CotaEsgotadaException}, que fala com o próprio Prestador (403), esta fala com o Empregador e
 * diz qual candidato não pôde ser escolhido; é 409 porque o impedimento é o estado do candidato, não a permissão de
 * quem pediu. {@code ErroResponse} não tem campo extra, então o nome vai na mensagem.
 */
public class CotaEsgotadaCandidatoException extends NegocioException {

    public CotaEsgotadaCandidatoException(String nomeCandidato) {
        super(CodigoErro.COTA_ESGOTADA_CANDIDATO, HttpStatus.CONFLICT,
                nomeCandidato + " atingiu o limite de escolhas do plano gratuito neste mês e não pode ser escolhido agora.");
    }
}
