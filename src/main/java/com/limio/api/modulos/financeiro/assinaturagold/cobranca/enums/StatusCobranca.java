package com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums;

/**
 * Estado de uma tentativa de cobrança no gateway (ETI-81). Nasce {@code PENDENTE} (reservada numa
 * transação curta, antes da chamada ao gateway — ver {@code AssinarGoldUseCase}) e vira {@code APROVADA}
 * ou {@code RECUSADA} na confirmação, numa segunda transação curta. Uma falha entre a cobrança e essa
 * confirmação deixa a linha {@code PENDENTE} — pendência conhecida de reconciliação via webhook.
 * {@code FALHOU}: o gateway não processou a requisição (fora do ar antes de receber a cobrança), então
 * nada foi cobrado e o Prestador pode tentar de novo.
 */
public enum StatusCobranca {
    PENDENTE,
    APROVADA,
    RECUSADA,
    FALHOU
}
