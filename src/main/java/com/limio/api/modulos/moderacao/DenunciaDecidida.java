package com.limio.api.modulos.moderacao;

import java.util.List;

import com.limio.api.modulos.moderacao.denuncia.DecisaoModeracao;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.Sancao;

/** Resultado da decisão (ETI-116): a denúncia resolvida, a decisão e a sanção aplicada, se houve. */
public record DenunciaDecidida(Denuncia denuncia, DecisaoModeracao decisao, List<Sancao> sancoes) {
}
