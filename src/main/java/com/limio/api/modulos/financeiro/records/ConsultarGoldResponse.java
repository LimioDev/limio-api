package com.limio.api.modulos.financeiro.records;

import com.limio.api.modulos.financeiro.plano.enums.CicloPlano;

/** {@code cartaoSalvo} é {@code null} quando o Prestador ainda não tem cartão salvo. */
public record ConsultarGoldResponse(String codigoPlano, String nome, long precoCentavos, String moeda,
        CicloPlano ciclo, CartaoSalvoResponse cartaoSalvo) {
}
