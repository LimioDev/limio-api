package com.limio.api.comum.portas.enums;

/**
 * Situação da conta vista de fora do {@code auth} (ETI-116). Junta o {@code StatusConta} com o encerramento, que no
 * {@code auth} é só {@code anonimizadoEm} preenchido.
 */
public enum SituacaoConta {
    ATIVA,
    PENDENTE_VERIFICACAO,
    SUSPENSA,
    ENCERRADA
}
