package com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums;

/**
 * Estado de uma tentativa de cobrança no gateway (ETI-81). Nasce {@code PENDENTE} (reservada numa
 * transação curta, antes da chamada ao gateway — ver {@code AssinarGoldUseCase}) e vira {@code APROVADA}
 * ou {@code RECUSADA} na confirmação, numa segunda transação curta. Uma falha entre a cobrança e essa
 * confirmação deixa a linha {@code PENDENTE} — pendência conhecida de reconciliação via webhook.
 */
public enum StatusCobranca {
    PENDENTE,
    APROVADA,
    RECUSADA
}
