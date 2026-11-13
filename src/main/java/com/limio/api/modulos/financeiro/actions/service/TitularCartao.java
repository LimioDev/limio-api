package com.limio.api.modulos.financeiro.actions.service;

/**
 * Dados de quem é titular do cartão tokenizado (ETI-81) — pode não ser quem está logado. Exigência do
 * Asaas pra tokenizar; tipo de domínio do módulo, não o record HTTP.
 */
public record TitularCartao(String nome, String email, String cpf, String cep, String numeroEndereco,
        String telefone) {
}
