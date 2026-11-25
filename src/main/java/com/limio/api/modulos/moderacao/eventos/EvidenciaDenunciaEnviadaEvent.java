package com.limio.api.modulos.moderacao.eventos;

/**
 * Publicado logo depois do upload da foto de uma denúncia (ETI-114). Se a transação desfizer,
 * {@code EvidenciaDenunciaEventosService} apaga o objeto {@code chave}, que ficou sem {@code Midia} apontando pra ele.
 * Interno ao {@code moderacao}.
 */
public record EvidenciaDenunciaEnviadaEvent(String chave) {
}
