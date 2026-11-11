package com.limio.api.modulos.financeiro.assinaturagold.enums;

/**
 * Estado de uma {@code AssinaturaGold} (ETI-81). {@code CANCELADA} ainda não é produzida por nenhum
 * fluxo hoje — nasce com o card futuro "Cancelar assinatura GOLD" (US26/US27) — mas já existe aqui
 * porque {@link com.limio.api.modulos.financeiro.actions.usecase.AssinarGoldUseCase} reativa a mesma
 * linha em vez de criar outra quando a encontra neste estado.
 */
public enum StatusAssinatura {
    ATIVA,
    CANCELADA
}
