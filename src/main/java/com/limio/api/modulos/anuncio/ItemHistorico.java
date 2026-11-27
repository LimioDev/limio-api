package com.limio.api.modulos.anuncio;

import com.limio.api.comum.portas.SituacaoAvaliacao;
import com.limio.api.modulos.anuncio.enums.StatusHistorico;

/**
 * Uma linha do histórico de anúncios e serviços (ETI-71), já resolvida para quem consulta. {@code outraParte} é o nome
 * da contraparte, {@code null} se não há uma (anúncio sem escolhido); {@code avaliacao} é {@code null} fora de serviço
 * concluído do qual o usuário participou.
 */
public record ItemHistorico(Anuncio anuncio, StatusHistorico status, String outraParte,
        SituacaoAvaliacao avaliacao) {
}
