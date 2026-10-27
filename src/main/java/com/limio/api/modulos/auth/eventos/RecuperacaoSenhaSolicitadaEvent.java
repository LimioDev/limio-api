package com.limio.api.modulos.auth.eventos;

/**
 * Publicado pela solicitação de recuperação de senha; {@code EmailEventosService} envia o link só depois
 * do commit, então ele nunca aponta para um token não persistido. Interno ao {@code auth}.
 */
public record RecuperacaoSenhaSolicitadaEvent(String destinatario, String link) {
}
