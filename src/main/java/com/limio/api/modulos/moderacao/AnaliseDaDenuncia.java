package com.limio.api.modulos.moderacao;

import java.util.List;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ContaParaModeracao;
import com.limio.api.comum.portas.MensagemOriginal;
import com.limio.api.modulos.moderacao.denuncia.DecisaoModeracao;
import com.limio.api.modulos.moderacao.denuncia.Denuncia;
import com.limio.api.modulos.moderacao.denuncia.Sancao;
import com.limio.api.modulos.moderacao.enums.TipoAlvoDenuncia;

/**
 * Tudo o que a tela de análise mostra de uma denúncia (ETI-116), antes de virar contrato HTTP.
 *
 * @param tituloAnuncio      título do anúncio ou serviço do contexto, nulo sem anúncio
 * @param denunciante        nulo se o id nunca existiu
 * @param denunciado         nulo se o id nunca existiu
 * @param evidencia          link da foto que o denunciante enviou, nulo sem foto ou com o storage fora do ar
 * @param fotosCheckout      fotos do check-out do serviço do contexto
 * @param chat               mensagens com o texto original, da mais antiga para a mais recente
 * @param outrasDenuncias    outras denúncias contra o mesmo denunciado, da mais recente para a mais antiga
 * @param sancoesAnteriores  sanções que o denunciado já recebeu, da mais recente para a mais antiga
 * @param decisao            nula enquanto a denúncia está aberta
 * @param sancoesDaDecisao   sanções aplicadas por esta decisão
 */
public record AnaliseDaDenuncia(Denuncia denuncia, TipoAlvoDenuncia tipoAlvo, String tituloAnuncio,
        ContaParaModeracao denunciante, ContaParaModeracao denunciado, LinkTemporario evidencia,
        List<LinkTemporario> fotosCheckout, List<MensagemOriginal> chat, List<Denuncia> outrasDenuncias,
        List<Sancao> sancoesAnteriores, DecisaoModeracao decisao, List<Sancao> sancoesDaDecisao) {
}
