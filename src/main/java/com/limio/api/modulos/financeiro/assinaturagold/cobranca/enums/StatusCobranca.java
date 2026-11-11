package com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums;

/**
 * Resultado de uma tentativa de cobrança no gateway (ETI-81). Só {@code APROVADA} é persistida hoje — uma
 * recusa não grava {@code Cobranca} (nada muda, o Prestador permanece FREE); o valor existe pra quando
 * alguma trilha de auditoria precisar registrar a recusa também.
 */
public enum StatusCobranca {
    APROVADA,
    RECUSADA
}
