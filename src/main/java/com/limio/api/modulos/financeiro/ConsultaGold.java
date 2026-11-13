package com.limio.api.modulos.financeiro;

import java.util.Optional;

import com.limio.api.modulos.financeiro.meiopagamento.MeioPagamento;
import com.limio.api.modulos.financeiro.plano.Plano;

/** O que a tela "Assinar GOLD" mostra (ETI-81): o plano ativo e o cartão salvo do Prestador, se houver. */
public record ConsultaGold(Plano plano, Optional<MeioPagamento> cartaoSalvo) {
}
