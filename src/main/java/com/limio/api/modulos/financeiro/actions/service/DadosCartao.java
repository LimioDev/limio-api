package com.limio.api.modulos.financeiro.actions.service;

/**
 * Dados crus de um cartão pra tokenizar no gateway (ETI-81) — nunca persistidos, nunca logados.
 * {@code holderName} é o nome impresso no cartão, que pode diferir do nome do {@link TitularCartao}
 * (cartão de terceiro). Tipo de domínio do módulo, não o record HTTP — o usecase não depende de record.
 */
public record DadosCartao(String holderName, String numero, String expiryMonth, String expiryYear, String ccv) {
}
