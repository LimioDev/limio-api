package com.limio.api.modulos.anuncio.eventos;

/**
 * Publicado quando o dono remove uma foto do anúncio (ETI-61). Depois do commit,
 * {@code FotoAnuncioEventosService} apaga o objeto {@code chave} do bucket; a {@code Midia} já saiu de cena
 * ({@code expurgoEm}). Interno ao {@code anuncio}.
 */
public record FotoAnuncioRemovidaEvent(String chave) {
}
