package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.CandidaturaEncerradaEvent;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.excecao.CandidaturaEstadoInvalidoException;
import com.limio.api.modulos.anuncio.excecao.CandidaturaNaoEncontradaException;
import com.limio.api.modulos.anuncio.excecao.UseCancelamentoServicoException;

/**
 * UC "Desistir da aplicação" (ETI-77, US22): o Prestador retira uma candidatura ainda {@code ENVIADA}. Ela
 * vai pra {@code ENCERRADA} (motivo "desistência"), sai da lista de candidatos do Empregador (que só lista
 * {@code ENVIADA}) e {@link CandidaturaEncerradaEvent} avisa o {@code chat} pra ficar somente leitura.
 * Não mexe na cota: só a escolha consome.
 *
 * Candidatura de outra pessoa responde como inexistente (404), mesmo critério de {@code
 * ConsultarMeuAnuncioUseCase}. A linha é travada na leitura ({@link AplicacaoService#buscarParaAtualizar})
 * pra que uma escolha simultânea do Empregador não seja sobrescrita.
 */
@Service
public class DesistirCandidaturaUseCase {

    private final AplicacaoService aplicacaoService;
    private final ApplicationEventPublisher eventos;

    public DesistirCandidaturaUseCase(AplicacaoService aplicacaoService, ApplicationEventPublisher eventos) {
        this.aplicacaoService = aplicacaoService;
        this.eventos = eventos;
    }

    @Transactional
    public void executar(UUID candidaturaId, UUID prestadorId) {
        Aplicacao aplicacao = aplicacaoService.buscarParaAtualizar(candidaturaId)
                .filter(encontrada -> encontrada.pertenceA(prestadorId))
                .orElseThrow(CandidaturaNaoEncontradaException::new);
        exigirEnviada(aplicacao);

        Instant agora = Instant.now();
        aplicacao.desistir(agora);
        aplicacaoService.salvar(aplicacao);

        eventos.publishEvent(new CandidaturaEncerradaEvent(aplicacao.getId(), aplicacao.getAnuncioId(),
                prestadorId, agora));
    }

    /** Já escolhido: o UC manda cancelar o serviço (ETI-96/ETI-97), não desistir da candidatura. */
    private static void exigirEnviada(Aplicacao aplicacao) {
        switch (aplicacao.getStatus()) {
            case ENVIADA -> {
            }
            case ESCOLHIDA, AGUARDANDO_VALIDACAO -> throw new UseCancelamentoServicoException();
            case RECUSADA, CONCLUIDA, ENCERRADA -> throw new CandidaturaEstadoInvalidoException();
        }
    }
}
