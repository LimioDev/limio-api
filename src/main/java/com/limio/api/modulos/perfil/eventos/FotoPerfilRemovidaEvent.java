package com.limio.api.modulos.perfil.eventos;

/**
 * Publicado quando a foto de perfil é tirada de uso sem outra no lugar (encerramento de conta). Depois
 * do commit, {@code FotoPerfilEventosService} apaga o objeto {@code chave} do storage. Interno ao
 * {@code perfil}.
 */
public record FotoPerfilRemovidaEvent(String chave) {
}
