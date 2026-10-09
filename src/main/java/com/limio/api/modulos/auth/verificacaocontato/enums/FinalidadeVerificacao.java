package com.limio.api.modulos.auth.verificacaocontato.enums;

/**
 * O que o token autoriza. Separado de {@link CanalVerificacao} porque
 * recuperação de senha e confirmação de e-mail saem pelo mesmo canal (EMAIL)
 * — sem este campo não dá pra diferenciar os dois links. Decisão do
 * TICKET-0033 no lugar do campo "proposito" que saiu do diagrama de classes.
 */
public enum FinalidadeVerificacao {
    CONFIRMACAO_EMAIL,
    RECUPERACAO_SENHA
}
