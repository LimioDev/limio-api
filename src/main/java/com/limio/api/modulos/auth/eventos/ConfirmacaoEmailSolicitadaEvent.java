package com.limio.api.modulos.auth.eventos;

/**
 * Publicado pela troca/reemissão de e-mail; {@code EmailEventosService} envia o
 * link só depois do commit, então ele nunca aponta para um token não persistido.
 * Interno ao {@code auth}: nenhum outro módulo precisa escutá-lo.
 */
public record ConfirmacaoEmailSolicitadaEvent(String destinatario, String link) {
}
