package com.limio.api.modulos.auth.verificacaocontato.enums;

/**
 * Meio de envio do token — independe do que ele autoriza (ver {@link FinalidadeVerificacao}). Só
 * {@code EMAIL} hoje: a confirmação de telefone por SMS (UC04) nunca chegou a existir e saiu de
 * escopo (ETI-134).
 */
public enum CanalVerificacao {
    EMAIL
}
