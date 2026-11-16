package com.limio.api.modulos.financeiro.assinaturagold.enums;

/**
 * Estado de uma {@code AssinaturaGold} (ETI-81). {@code PENDENTE}: reservada numa transação curta,
 * cobrança em andamento ou ainda não confirmada fora dela — ver
 * {@link com.limio.api.modulos.financeiro.actions.usecase.AssinarGoldUseCase}. {@code CANCELADA} cobre
 * tanto o card futuro "Cancelar assinatura GOLD" (US26/US27, ainda sem fluxo que produza) quanto uma
 * tentativa nova recusada pelo gateway (nunca chegou a ficar {@code ATIVA}) — os dois casos reativam a
 * mesma linha em vez de criar outra quando o usuário assina de novo.
 */
public enum StatusAssinatura {
    PENDENTE,
    ATIVA,
    CANCELADA
}
