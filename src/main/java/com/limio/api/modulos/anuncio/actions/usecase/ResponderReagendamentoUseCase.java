package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.ReagendamentoAceitoEvent;
import com.limio.api.comum.eventos.ReagendamentoRecusadoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.DecisaoReagendamento;
import com.limio.api.modulos.anuncio.enums.SituacaoCancelamentoServico;
import com.limio.api.modulos.anuncio.excecao.DataNoPassadoException;
import com.limio.api.modulos.anuncio.excecao.ProponenteNaoRespondeException;
import com.limio.api.modulos.anuncio.excecao.PropostaEncerradaException;
import com.limio.api.modulos.anuncio.excecao.PropostaExpiradaException;
import com.limio.api.modulos.anuncio.excecao.ReagendamentoNaoEncontradoException;
import com.limio.api.modulos.anuncio.excecao.ServicoEstadoInvalidoException;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * A contraparte aceita ou recusa a proposta de reagendamento (ETI-99, US47).
 *
 * <p>Validações comuns, nesta ordem: a proposta existe e o usuário é parte do serviço (senão 404); não é quem propôs
 * (senão 403); está pendente (senão 409 {@code PROPOSTA_ENCERRADA}); e ainda está dentro do prazo (senão 409 {@code
 * PROPOSTA_EXPIRADA}: a vencida é encerrada pelo job de expiração, que grava {@code EXPIRADO}).
 *
 * <ul>
 *   <li>{@code ACEITAR}: o serviço ainda precisa estar combinado e a data proposta, ainda no futuro (no momento do
 *       aceite, não só na criação). A data vigente do anúncio passa a ser a proposta; o anúncio segue {@code
 *       PUBLICADO} e a candidatura, {@code ESCOLHIDA}. {@code respondidoEm} é o início da carência
 *       pós-reagendamento (RB36, lida pelo cancelamento do Empregador) e o contador de aceitos sobe por consequência,
 *       porque é a contagem de propostas {@code ACEITO}. Nenhuma cota é tocada.
 *   <li>{@code RECUSAR}: a proposta é encerrada e a data original continua vigente.
 * </ul>
 *
 * <p>Trava: o {@code anuncioId} sai de uma consulta escalar, a trava advisory {@code ANUNCIO} é tomada, e só então a
 * proposta é lida — aceitar, recusar, cancelar, expirar e cancelar ou iniciar o serviço se serializam nela, e quem
 * perde a corrida vê a proposta já encerrada.
 */
@Service
public class ResponderReagendamentoUseCase {

    private static final String ORIGEM_AUDITORIA = "anuncio-reagendamento-responder";

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;
    private final LogAuditoriaService logAuditoriaService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;
    private final Clock relogio;

    public ResponderReagendamentoUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService, LogAuditoriaService logAuditoriaService,
            ApplicationEventPublisher eventos, TravaTransacional trava, Clock relogio) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.logAuditoriaService = logAuditoriaService;
        this.eventos = eventos;
        this.trava = trava;
        this.relogio = relogio;
    }

    @Transactional
    public Reagendamento executar(UUID usuarioId, UUID reagendamentoId, DecisaoReagendamento decisao, String ip) {
        UUID anuncioId = reagendamentoService.buscarAnuncioId(reagendamentoId)
                .orElseThrow(ReagendamentoNaoEncontradoException::new);
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Reagendamento proposta = reagendamentoService.buscarPorId(reagendamentoId)
                .orElseThrow(ReagendamentoNaoEncontradoException::new);
        Anuncio anuncio = anuncioService.buscarPorId(anuncioId).orElseThrow(ReagendamentoNaoEncontradoException::new);
        UUID prestadorId = aplicacaoService.buscarPrestadorEscolhido(anuncioId).orElse(null);

        if (!anuncio.pertenceA(usuarioId) && !usuarioId.equals(prestadorId)) {
            throw new ReagendamentoNaoEncontradoException();
        }
        if (proposta.foiProposta(usuarioId)) {
            throw new ProponenteNaoRespondeException();
        }
        if (!proposta.estaPendente()) {
            throw new PropostaEncerradaException();
        }
        Instant agora = relogio.instant();
        if (proposta.estaVencida(agora)) {
            throw new PropostaExpiradaException();
        }

        switch (decisao) {
            case ACEITAR -> aceitar(proposta, anuncio, prestadorId, agora, usuarioId, ip);
            case RECUSAR -> recusar(proposta, anuncio, prestadorId, agora, usuarioId, ip);
        }
        return proposta;
    }

    private void aceitar(Reagendamento proposta, Anuncio anuncio, UUID prestadorId, Instant agora, UUID respondenteId,
            String ip) {
        exigirServicoCombinado(anuncio);
        if (!proposta.getDataProposta().isAfter(agora)) {
            throw new DataNoPassadoException();
        }

        Instant dataAnterior = anuncio.getDataHoraPretendida();
        anuncio.reagendar(proposta.getDataProposta());
        anuncioService.salvar(anuncio);
        proposta.aceitar(agora);
        reagendamentoService.salvar(proposta);

        Map<String, String> detalhes = detalhes(proposta, respondenteId);
        detalhes.put("dataAnterior", dataAnterior.toString());
        detalhes.put("dataNova", proposta.getDataProposta().toString());
        detalhes.put("aceitoEm", agora.toString());
        logAuditoriaService.registrar("Reagendamento", proposta.getId(), TipoAcaoAuditoria.REAGENDAMENTO_ACEITO,
                ORIGEM_AUDITORIA, ip, detalhes);
        eventos.publishEvent(new ReagendamentoAceitoEvent(proposta.getId(), anuncio.getId(), anuncio.getTitulo(),
                anuncio.getContratanteId(), prestadorId, proposta.getProponenteId(), dataAnterior,
                proposta.getDataProposta(), agora));
    }

    private void recusar(Reagendamento proposta, Anuncio anuncio, UUID prestadorId, Instant agora, UUID respondenteId,
            String ip) {
        proposta.recusar(agora);
        reagendamentoService.salvar(proposta);

        Map<String, String> detalhes = detalhes(proposta, respondenteId);
        detalhes.put("dataVigente", anuncio.getDataHoraPretendida().toString());
        logAuditoriaService.registrar("Reagendamento", proposta.getId(), TipoAcaoAuditoria.REAGENDAMENTO_RECUSADO,
                ORIGEM_AUDITORIA, ip, detalhes);
        eventos.publishEvent(new ReagendamentoRecusadoEvent(proposta.getId(), anuncio.getId(), anuncio.getTitulo(),
                anuncio.getContratanteId(), prestadorId, proposta.getProponenteId(), anuncio.getDataHoraPretendida(),
                proposta.getDataProposta(), agora));
    }

    /** "Combinado" é {@code PUBLICADO} com a candidatura {@code ESCOLHIDA}, a mesma regra de propor e de cancelar. */
    private void exigirServicoCombinado(Anuncio anuncio) {
        boolean combinado = anuncio.situacaoParaCancelarServico() == SituacaoCancelamentoServico.CANCELAVEL
                && aplicacaoService.buscarEscolhidaDoAnuncioParaAtualizar(anuncio.getId()).isPresent();
        if (!combinado) {
            throw new ServicoEstadoInvalidoException();
        }
    }

    private static Map<String, String> detalhes(Reagendamento proposta, UUID respondenteId) {
        Map<String, String> detalhes = new LinkedHashMap<>();
        detalhes.put("anuncioId", proposta.getAnuncioId().toString());
        detalhes.put("proponenteId", proposta.getProponenteId().toString());
        detalhes.put("respondenteId", respondenteId.toString());
        return detalhes;
    }
}
