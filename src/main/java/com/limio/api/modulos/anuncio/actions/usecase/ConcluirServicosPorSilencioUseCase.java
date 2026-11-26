package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.ServicoConcluidoEvent;
import com.limio.api.comum.eventos.enums.OrigemConclusao;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAnuncio;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * Rotina que conclui os serviços por silêncio (ETI-98). {@link #executar()} não é transacional: cada anúncio roda em
 * transação própria, e a decisão é tomada sob a trava {@code ANUNCIO} com o estado atual, o que torna a rotina
 * idempotente e segura com várias instâncias. Conta encerrada com anúncio ainda aberto não é concluída: o
 * encerramento é do fluxo de saída de conta (TICKET-0049).
 */
@Service
public class ConcluirServicosPorSilencioUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConcluirServicosPorSilencioUseCase.class);
    private static final String ORIGEM_AUDITORIA = "anuncio-concluir-silencio";

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;
    private final ConsultaDadosUsuario consultaDadosUsuario;
    private final LogAuditoriaService logAuditoriaService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;
    private final TransactionTemplate transacao;
    private final Clock relogio;
    private final Duration prazoPosAgenda;
    private final int prazoValidacaoHoras;
    private final Duration prazoValidacaoPadrao;
    private final int lote;

    public ConcluirServicosPorSilencioUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService, ConsultaDadosUsuario consultaDadosUsuario,
            LogAuditoriaService logAuditoriaService, ApplicationEventPublisher eventos, TravaTransacional trava,
            TransactionTemplate transacao, Clock relogio,
            @Value("${anuncio.prazo-pos-agenda-horas}") int prazoPosAgendaHoras,
            @Value("${anuncio.prazo-validacao-horas}") int prazoValidacaoHoras,
            @Value("${anuncio.conclusao-silencio.lote}") int lote) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.consultaDadosUsuario = consultaDadosUsuario;
        this.logAuditoriaService = logAuditoriaService;
        this.eventos = eventos;
        this.trava = trava;
        this.transacao = transacao;
        this.relogio = relogio;
        this.prazoPosAgenda = Duration.ofHours(prazoPosAgendaHoras);
        this.prazoValidacaoHoras = prazoValidacaoHoras;
        this.prazoValidacaoPadrao = Duration.ofHours(prazoValidacaoHoras);
        this.lote = lote;
    }

    /**
     * Conclui até {@code lote} serviços. Anúncio que não conclui (conta encerrada, falha) continua na pré-seleção, e
     * como ela vem do mais antigo para o mais novo, a busca avança além dele para não travar os demais.
     */
    @Scheduled(cron = "${anuncio.conclusao-silencio.cron}")
    public void executar() {
        Instant agora = relogio.instant();
        int concluidos = 0;
        int naoConcluidos = 0;
        List<UUID> candidatos;
        do {
            candidatos = anuncioService.buscarIdsParaConcluirPorSilencio(agora, agora.minus(prazoPosAgenda),
                    prazoValidacaoHoras, lote, naoConcluidos);
            for (UUID anuncioId : candidatos) {
                if (concluidos == lote) {
                    break;
                }
                if (tentarConcluir(anuncioId, agora)) {
                    concluidos++;
                } else {
                    naoConcluidos++;
                }
            }
        } while (candidatos.size() == lote && concluidos < lote);

        if (concluidos + naoConcluidos > 0) {
            log.info("conclusão por silêncio: {} concluídos e {} não concluídos", concluidos, naoConcluidos);
        }
    }

    private boolean tentarConcluir(UUID anuncioId, Instant agora) {
        try {
            return Boolean.TRUE.equals(transacao.execute(status -> concluirUm(anuncioId, agora)));
        } catch (RuntimeException e) {
            log.error("falha ao concluir por silêncio o anúncio {}; os demais continuam", anuncioId, e);
            return false;
        }
    }

    private boolean concluirUm(UUID anuncioId, Instant agora) {
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Optional<Anuncio> vencido = anuncioService.buscarPorId(anuncioId).filter(
                anuncio -> anuncio.permiteConclusaoPorSilencio(agora, prazoPosAgenda, prazoValidacaoPadrao));
        if (vencido.isEmpty()) {
            return false;
        }
        Anuncio anuncio = vencido.get();

        Optional<UUID> escolhido = aplicacaoService.buscarEscolhidaVigente(anuncioId).map(Aplicacao::getPrestadorId);
        if (escolhido.isEmpty() || reagendamentoService.existePendente(anuncioId)) {
            return false;
        }
        UUID contratanteId = anuncio.getContratanteId();
        UUID prestadorId = escolhido.get();
        if (contaEncerrada(contratanteId) || contaEncerrada(prestadorId)) {
            log.warn("anúncio {} vencido não foi concluído: uma das partes encerrou a conta e ele continua aberto",
                    anuncioId);
            return false;
        }

        StatusAnuncio statusAnterior = anuncio.getStatus();
        anuncio.concluirPorSilencio(agora);
        anuncioService.salvar(anuncio);

        Map<String, String> detalhes = new LinkedHashMap<>();
        detalhes.put("statusAnterior", statusAnterior.name());
        detalhes.put("contratanteId", contratanteId.toString());
        detalhes.put("prestadorId", prestadorId.toString());
        detalhes.put("dataVigente", String.valueOf(anuncio.getDataHoraPretendida()));
        detalhes.put("checkoutEm", String.valueOf(anuncio.getCheckoutEm()));
        logAuditoriaService.registrar("Anuncio", anuncioId, TipoAcaoAuditoria.SERVICO_CONCLUIDO_POR_SILENCIO,
                ORIGEM_AUDITORIA, null, detalhes);
        eventos.publishEvent(new ServicoConcluidoEvent(anuncioId, anuncio.getTitulo(), contratanteId, prestadorId,
                OrigemConclusao.SILENCIO, agora));
        return true;
    }

    private boolean contaEncerrada(UUID usuarioId) {
        return consultaDadosUsuario.buscarPorId(usuarioId).isEmpty();
    }
}
