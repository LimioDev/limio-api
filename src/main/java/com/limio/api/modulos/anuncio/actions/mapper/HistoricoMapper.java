package com.limio.api.modulos.anuncio.actions.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.SituacaoAvaliacao;
import com.limio.api.comum.records.PageResponse;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.ItemHistorico;
import com.limio.api.modulos.anuncio.records.AvaliacaoHistoricoResponse;
import com.limio.api.modulos.anuncio.records.HistoricoItemResponse;

@Component
public class HistoricoMapper {

    public PageResponse<HistoricoItemResponse> toResponse(Page<ItemHistorico> pagina) {
        return PageResponse.de(pagina, HistoricoMapper::toResponse);
    }

    private static HistoricoItemResponse toResponse(ItemHistorico item) {
        Anuncio anuncio = item.anuncio();
        return new HistoricoItemResponse(
                anuncio.getId(),
                anuncio.getTitulo(),
                anuncio.desfechoEm(),
                item.status(),
                item.outraParte(),
                toResponse(item.avaliacao()));
    }

    private static AvaliacaoHistoricoResponse toResponse(SituacaoAvaliacao situacao) {
        return situacao == null ? null
                : new AvaliacaoHistoricoResponse(situacao.notaRecebida(), situacao.podeAvaliar());
    }
}
