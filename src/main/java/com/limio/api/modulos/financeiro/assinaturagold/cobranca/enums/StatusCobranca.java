package com.limio.api.modulos.financeiro.assinaturagold.cobranca.enums;

/**
 * Estado de uma tentativa de cobrança no gateway. Nasce {@code PENDENTE} (reservada numa transação curta, antes da
 * chamada ao gateway — ver {@code AssinarGoldUseCase} e {@code RenovarGoldUseCase}) e vira {@code APROVADA} ou
 * {@code RECUSADA} na confirmação, numa segunda transação curta, ou pelo aviso do gateway (ETI-85).
 *
 * <p>{@code FALHOU} (ETI-85): o gateway não respondeu na renovação. Não conta como tentativa recusada nem avisa o
 * Prestador; a renovação tenta de novo depois. Como o gateway pode ter cobrado sem responder, o aviso dele ainda
 * resolve esta linha para {@code APROVADA} ou {@code RECUSADA}.
 */
public enum StatusCobranca {
    PENDENTE,
    APROVADA,
    RECUSADA,
    FALHOU
}
