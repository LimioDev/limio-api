package com.limio.api.modulos.moderacao;

import com.limio.api.comum.portas.ContaParaModeracao;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;

/** Uma linha da fila de denúncias (ETI-116): a denúncia e o denunciado, nulo se o id nunca existiu. */
public record ItemDaFila(Denuncia denuncia, ContaParaModeracao denunciado) {
}
