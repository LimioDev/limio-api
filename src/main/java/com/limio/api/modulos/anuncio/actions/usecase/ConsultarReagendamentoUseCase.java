package com.limio.api.modulos.anuncio.actions.usecase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.ReagendamentoConsultado;
import com.limio.api.modulos.anuncio.actions.helper.PropostaSobTravaHelper;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.ReagendamentoNaoEncontradoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * Consultar uma proposta de reagendamento (ETI-99): data vigente, data proposta, justificativa, proponente e prazo de
 * resposta. Só leitura, sem trava. Enxerga a proposta quem é parte do serviço (o Empregador ou o Prestador escolhido)
 * e, em qualquer caso, quem a propôs; proposta inexistente e de serviço alheio têm a mesma resposta (404).
 */
@Service
public class ConsultarReagendamentoUseCase {

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;

    public ConsultarReagendamentoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
    }

    @Transactional(readOnly = true)
    public ReagendamentoConsultado executar(UUID usuarioId, UUID reagendamentoId) {
        Reagendamento proposta = reagendamentoService.buscarPorId(reagendamentoId)
                .orElseThrow(ReagendamentoNaoEncontradoException::new);
        Anuncio anuncio = anuncioService.buscarPorId(proposta.getAnuncioId())
                .orElseThrow(ReagendamentoNaoEncontradoException::new);

        boolean parte = proposta.foiProposta(usuarioId) || PropostaSobTravaHelper.ehParteDoServico(anuncio,
                aplicacaoService.buscarPrestadorEscolhido(anuncio.getId()).orElse(null), usuarioId);
        if (!parte) {
            throw new ReagendamentoNaoEncontradoException();
        }
        return new ReagendamentoConsultado(proposta, anuncio.getDataHoraPretendida());
    }
}
