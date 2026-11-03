package com.limio.api.modulos.moderacao.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.comum.portas.ResumoServico;
import com.limio.api.modulos.moderacao.AvaliacaoPendente;
import com.limio.api.modulos.moderacao.OutraParte;
import com.limio.api.modulos.moderacao.avaliacao.Avaliacao;
import com.limio.api.modulos.moderacao.records.AvaliacaoPendenteResponse;
import com.limio.api.modulos.moderacao.records.AvaliacaoResponse;

@Component
public class AvaliacaoMapper {

    public AvaliacaoPendenteResponse toResponse(AvaliacaoPendente pendente) {
        ResumoServico servico = pendente.servico();
        OutraParte outraParte = pendente.outraParte();
        return new AvaliacaoPendenteResponse(
                new AvaliacaoPendenteResponse.Servico(servico.anuncioId(), servico.titulo(), servico.concluidoEm()),
                new AvaliacaoPendenteResponse.OutraParte(outraParte.nome(), outraParte.fotoUrl(),
                        outraParte.fotoExpiraEm(), outraParte.papel()),
                pendente.podeAvaliar(),
                pendente.avaliarAte());
    }

    public AvaliacaoResponse toResponse(Avaliacao avaliacao) {
        return new AvaliacaoResponse(
                avaliacao.getId(),
                avaliacao.getAnuncioId(),
                avaliacao.getNota(),
                avaliacao.getComentario(),
                avaliacao.getPublicadaEm());
    }
}
