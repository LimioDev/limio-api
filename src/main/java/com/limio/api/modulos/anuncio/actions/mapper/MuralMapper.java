package com.limio.api.modulos.anuncio.actions.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.records.PageResponse;
import com.limio.api.modulos.anuncio.DetalheDoMural;
import com.limio.api.modulos.anuncio.FotoDoAnuncio;
import com.limio.api.modulos.anuncio.ItemDoMural;
import com.limio.api.modulos.anuncio.records.CandidaturaResponse;
import com.limio.api.modulos.anuncio.records.CategoriaAnuncioResponse;
import com.limio.api.modulos.anuncio.records.EmpregadorNoDetalheResponse;
import com.limio.api.modulos.anuncio.records.EmpregadorNoMuralResponse;
import com.limio.api.modulos.anuncio.records.FotoAnuncioResponse;
import com.limio.api.modulos.anuncio.records.MuralDetalheResponse;
import com.limio.api.modulos.anuncio.records.MuralItemResponse;

@Component
public class MuralMapper {

    public PageResponse<MuralItemResponse> toResponse(Page<ItemDoMural> pagina) {
        return PageResponse.de(pagina, MuralMapper::toResponse);
    }

    public MuralDetalheResponse toResponse(DetalheDoMural detalhe) {
        return new MuralDetalheResponse(
                detalhe.id(),
                detalhe.titulo(),
                detalhe.descricao(),
                new CategoriaAnuncioResponse(detalhe.categoria().id(), detalhe.categoria().nome(),
                        detalhe.categoria().grupo()),
                detalhe.fotos().stream().map(MuralMapper::toResponse).toList(),
                detalhe.dataHoraPretendida(),
                detalhe.bairroRegiao(),
                detalhe.cidade(),
                detalhe.uf(),
                umaCasa(detalhe.distanciaKm()),
                new EmpregadorNoDetalheResponse(detalhe.empregador().nomeExibicao(), detalhe.empregador().fotoUrl(),
                        detalhe.empregador().mediaAvaliacoes(), detalhe.empregador().qtdAvaliacoes()),
                detalhe.minhaCandidatura() == null ? null
                        : new CandidaturaResponse(detalhe.minhaCandidatura().id(), detalhe.minhaCandidatura().status()));
    }

    private static FotoAnuncioResponse toResponse(FotoDoAnuncio foto) {
        LinkTemporario link = foto.link();
        return new FotoAnuncioResponse(foto.id(), link == null ? null : link.url(),
                link == null ? null : link.expiraEm());
    }

    private static MuralItemResponse toResponse(ItemDoMural item) {
        return new MuralItemResponse(
                item.id(),
                item.titulo(),
                new CategoriaAnuncioResponse(item.categoria().id(), item.categoria().nome(), item.categoria().grupo()),
                item.primeiraFoto() == null ? null : item.primeiraFoto().url(),
                new EmpregadorNoMuralResponse(item.empregador().nomeExibicao(), item.empregador().fotoUrl()),
                item.dataHoraPretendida(),
                item.bairroRegiao(),
                umaCasa(item.distanciaKm()));
    }

    /** 0,1 km basta pra tela e não entrega a posição exata (várias distâncias exatas permitem triangular). */
    static double umaCasa(double km) {
        return Math.round(km * 10) / 10.0;
    }
}
