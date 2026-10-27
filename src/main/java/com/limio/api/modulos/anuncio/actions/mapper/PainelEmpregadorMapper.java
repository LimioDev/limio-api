package com.limio.api.modulos.anuncio.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioNoPainel;
import com.limio.api.modulos.anuncio.PainelEmpregador;
import com.limio.api.modulos.anuncio.records.AnuncioPainelResponse;
import com.limio.api.modulos.anuncio.records.PainelEmpregadorResponse;

@Component
public class PainelEmpregadorMapper {

    public PainelEmpregadorResponse toResponse(PainelEmpregador painel) {
        return new PainelEmpregadorResponse(
                painel.perfil().nomeExibicao(),
                painel.perfil().fotoUrl(),
                painel.perfil().fotoExpiraEm(),
                painel.anunciosAbertos(),
                painel.novosCandidatos(),
                painel.anuncios().stream().map(PainelEmpregadorMapper::toResponse).toList());
    }

    private static AnuncioPainelResponse toResponse(AnuncioNoPainel item) {
        Anuncio anuncio = item.anuncio();
        return new AnuncioPainelResponse(
                anuncio.getId(),
                null,
                anuncio.getDescricao(),
                anuncio.getDataHoraPretendida(),
                anuncio.getBairroRegiaoSnapshot(),
                anuncio.getStatus(),
                item.candidatos(),
                item.prestadorEscolhido());
    }
}
