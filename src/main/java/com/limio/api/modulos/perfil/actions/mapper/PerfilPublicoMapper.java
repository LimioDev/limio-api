package com.limio.api.modulos.perfil.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.AvaliacaoRecente;
import com.limio.api.modulos.perfil.PerfilPublico;
import com.limio.api.modulos.perfil.records.AvaliacaoRecenteResponse;
import com.limio.api.modulos.perfil.records.PerfilPublicoResponse;

@Component
public class PerfilPublicoMapper {

    public PerfilPublicoResponse toResponse(PerfilPublico perfil) {
        return new PerfilPublicoResponse(
                perfil.nomeExibicao(),
                perfil.fotoUrl(),
                perfil.fotoExpiraEm(),
                perfil.mediaAvaliacoes(),
                perfil.qtdAvaliacoes(),
                perfil.servicosConcluidos(),
                perfil.especialidades(),
                perfil.avaliacoesRecentes().stream().map(this::toResponse).toList());
    }

    private AvaliacaoRecenteResponse toResponse(AvaliacaoRecente avaliacao) {
        return new AvaliacaoRecenteResponse(avaliacao.avaliadorNome(), avaliacao.nota(), avaliacao.comentario(),
                avaliacao.data());
    }
}
