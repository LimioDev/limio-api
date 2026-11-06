package com.limio.api.modulos.anuncio.actions.mapper;

import org.springframework.stereotype.Component;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.ResumoChatDoServico;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.OutraParteDoServico;
import com.limio.api.modulos.anuncio.ServicoCombinado;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.records.CategoriaAnuncioResponse;
import com.limio.api.modulos.anuncio.records.OutraParteServicoResponse;
import com.limio.api.modulos.anuncio.records.PropostaReagendamentoResponse;
import com.limio.api.modulos.anuncio.records.ServicoCombinadoResponse;

@Component
public class ServicoCombinadoMapper {

    public ServicoCombinadoResponse toResponse(ServicoCombinado servico) {
        Anuncio anuncio = servico.anuncio();
        Reagendamento proposta = servico.propostaPendente();
        ResumoChatDoServico chat = servico.chat();
        return new ServicoCombinadoResponse(
                anuncio.getId(),
                anuncio.getStatus(),
                anuncio.getTitulo(),
                anuncio.getDescricao(),
                new CategoriaAnuncioResponse(servico.categoria().id(), servico.categoria().nome(),
                        servico.categoria().grupo()),
                anuncio.getDataHoraPretendida(),
                anuncio.getBairroRegiaoSnapshot(),
                anuncio.getEnderecoCompletoSnapshot(),
                toResponse(servico.outraParte()),
                servico.escolhidaEm(),
                anuncio.getIniciadoEm(),
                anuncio.getCheckoutEm(),
                anuncio.getValidadoEm(),
                servico.prazoCancelamentoEm(),
                servico.reagendamentosAceitos(),
                servico.limiteReagendamentos(),
                proposta == null ? null : new PropostaReagendamentoResponse(proposta.getDataProposta(),
                        proposta.getJustificativa()),
                chat == null ? null : chat.chatId(),
                chat == null ? 0 : chat.mensagensNaoLidas());
    }

    private static OutraParteServicoResponse toResponse(OutraParteDoServico outraParte) {
        LinkTemporario foto = outraParte.foto();
        return new OutraParteServicoResponse(outraParte.nome(), foto == null ? null : foto.url(),
                foto == null ? null : foto.expiraEm(), outraParte.telefone(), outraParte.gold());
    }
}
