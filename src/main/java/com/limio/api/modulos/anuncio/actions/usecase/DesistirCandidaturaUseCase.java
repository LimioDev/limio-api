package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.eventos.CandidaturaEncerradaEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
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
 * ConsultarMeuAnuncioUseCase}.
 *
 * Trava em duas etapas, na ordem do agregado (ver {@link EscopoTrava#ANUNCIO}): primeiro o advisory lock do
 * anúncio, que coloca a desistência em fila com recusa, escolha e cancelamento; depois a linha da candidatura
 * ({@link AplicacaoService#buscarParaAtualizar}), carregada só depois do lock. Antes dele lê-se apenas o
 * {@code anuncioId} ({@link AplicacaoService#buscarAnuncioId}, consulta escalar que não gerencia a entidade),
 * pra saber qual trava tomar; o dono e o estado que decidem a desistência são os lidos depois dela.
 */
@Service
public class DesistirCandidaturaUseCase {

    private final AplicacaoService aplicacaoService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;

    public DesistirCandidaturaUseCase(AplicacaoService aplicacaoService, ApplicationEventPublisher eventos,
            TravaTransacional trava) {
        this.aplicacaoService = aplicacaoService;
        this.eventos = eventos;
        this.trava = trava;
    }

    @Transactional
    public void executar(UUID candidaturaId, UUID prestadorId) {
        UUID anuncioId = aplicacaoService.buscarAnuncioId(candidaturaId)
                .orElseThrow(CandidaturaNaoEncontradaException::new);
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

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
