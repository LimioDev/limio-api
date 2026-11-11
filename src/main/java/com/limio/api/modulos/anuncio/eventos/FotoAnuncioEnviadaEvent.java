package com.limio.api.modulos.anuncio.eventos;

/**
 * Publicado logo depois de cada upload de foto de anúncio ou de evidência de check-out (ETI-90, mesma limpeza). Se a transação desfizer,
 * {@code FotoAnuncioEventosService} apaga o objeto {@code chave}, que ficou sem {@code Midia} apontando pra ele.
 * Interno ao {@code anuncio}.
 */
public record FotoAnuncioEnviadaEvent(String chave) {
}
