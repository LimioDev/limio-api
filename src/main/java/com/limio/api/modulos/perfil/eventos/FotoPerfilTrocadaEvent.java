package com.limio.api.modulos.perfil.eventos;

/**
 * Publicado pela troca da foto de perfil, logo depois do upload do objeto novo. Depois do commit,
 * {@code FotoPerfilEventosService} apaga o objeto anterior ({@code chaveAnterior}, nula na primeira
 * foto); se a transação falhar, apaga o objeto novo, que não chegou a ser registrado.
 * Interno ao {@code perfil}.
 */
public record FotoPerfilTrocadaEvent(String chaveNova, String chaveAnterior) {
}
