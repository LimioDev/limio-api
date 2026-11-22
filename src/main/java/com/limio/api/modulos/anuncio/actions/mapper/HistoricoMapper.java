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

    private static final int TAMANHO_TRECHO = 60;

    public PageResponse<HistoricoItemResponse> toResponse(Page<ItemHistorico> pagina) {
        return PageResponse.de(pagina, HistoricoMapper::toResponse);
    }

    private static HistoricoItemResponse toResponse(ItemHistorico item) {
        Anuncio anuncio = item.anuncio();
        return new HistoricoItemResponse(
                anuncio.getId(),
                titulo(anuncio),
                anuncio.getValidadoEm() != null ? anuncio.getValidadoEm() : anuncio.getEncerradoEm(),
                item.status(),
                item.outraParte(),
                toResponse(item.avaliacao()));
    }

    private static AvaliacaoHistoricoResponse toResponse(SituacaoAvaliacao situacao) {
        return situacao == null ? null
                : new AvaliacaoHistoricoResponse(situacao.notaRecebida(), situacao.podeAvaliar());
    }

    private static String titulo(Anuncio anuncio) {
        String titulo = anuncio.getTitulo();
        if (titulo != null && !titulo.isBlank()) {
            return titulo;
        }
        String descricao = anuncio.getDescricao() == null ? "" : anuncio.getDescricao().strip();
        return descricao.length() <= TAMANHO_TRECHO ? descricao : descricao.substring(0, TAMANHO_TRECHO) + "…";
    }
}
