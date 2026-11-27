package com.limio.api.modulos.financeiro.assinaturagold.enums;

/**
 * Estado de uma {@code AssinaturaGold}.
 *
 * <ul>
 *   <li>{@code PENDENTE}: reservada numa transação curta, cobrança da assinatura em andamento ou ainda não confirmada
 *   fora dela (ETI-81, ver {@code AssinarGoldUseCase}). O aviso do gateway resolve a pendência (ETI-85).</li>
 *   <li>{@code ATIVA}: GOLD. Continua {@code ATIVA} durante as novas tentativas de uma renovação recusada (ETI-85):
 *   o Prestador não perde o GOLD por um problema pontual.</li>
 *   <li>{@code CANCELADA}: a cobrança da assinatura foi recusada e a linha nunca chegou a ficar {@code ATIVA}.</li>
 *   <li>{@code ENCERRADA}: voltou para FREE depois de ter sido GOLD (ETI-85), por tentativas de renovação esgotadas
 *   ou por fim do ciclo com a renovação cancelada. Grava {@code encerradaEm}.</li>
 * </ul>
 *
 * {@code CANCELADA} e {@code ENCERRADA} reativam a mesma linha quando o usuário assina de novo.
 */
public enum StatusAssinatura {
    PENDENTE,
    ATIVA,
    CANCELADA,
    ENCERRADA
}
