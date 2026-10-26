package com.limio.api.modulos.auth.eventos;

/**
 * Publicado pela alteração de senha do usuário logado; o aviso só sai depois do commit, então ninguém é
 * avisado de uma troca que foi desfeita. Interno ao {@code auth}.
 */
public record SenhaAlteradaEvent(String destinatario) {
}
