package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Clock;
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
import com.limio.api.comum.eventos.ReagendamentoExpiradoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * Rotina periódica que expira as propostas de reagendamento sem resposta no prazo (ETI-99, US47). Cada proposta
 * vencida é encerrada ({@code EXPIRADO}) em transação própria, aberta com {@link TransactionTemplate}: a data original
 * continua vigente, o serviço continua combinado, e a auditoria e o {@link ReagendamentoExpiradoEvent} saem dentro da
 * transação (o evento só é entregue após o commit, ADR-0003; o aviso às partes é da US56).
 *
 * <p>O método {@link #executar()} não é transacional: uma falha numa proposta é logada sem impedir as outras (mesmo
 * padrão de {@code ExpirarAnunciosSemCandidatosUseCase}, ETI-66).
 *
 * <p>Idempotente e seguro contra corrida: {@link ReagendamentoService#buscarIdsParaExpirar} é só pré-seleção. A decisão
 * é tomada em {@link #expirarUm}, sob a trava advisory {@code ANUNCIO} (a mesma de aceitar, recusar, cancelar, propor,
 * iniciar e cancelar o serviço), com o estado atual: quem chegar depois de uma resposta, de um cancelamento ou de outra
 * instância do job vê a proposta como ela ficou e sai sem efeito, auditoria nem evento. Assim uma proposta já
 * respondida nunca é expirada depois. Sem ShedLock: o advisory lock e a nova checagem bastam com várias instâncias.
 * Processa até {@code lote} por execução; sobrando, a execução seguinte continua.
 */
@Service
public class ExpirarReagendamentosUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpirarReagendamentosUseCase.class);
    private static final String ORIGEM_AUDITORIA = "anuncio-reagendamento-expirar";

    private final ReagendamentoService reagendamentoService;
    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final LogAuditoriaService logAuditoriaService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;
    private final TransactionTemplate transacao;
    private final Clock relogio;
    private final int lote;

    public ExpirarReagendamentosUseCase(ReagendamentoService reagendamentoService, AnuncioService anuncioService,
            AplicacaoService aplicacaoService, LogAuditoriaService logAuditoriaService,
            ApplicationEventPublisher eventos, TravaTransacional trava, TransactionTemplate transacao, Clock relogio,
            @Value("${anuncio.reagendamento.expiracao.lote}") int lote) {
        this.reagendamentoService = reagendamentoService;
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.logAuditoriaService = logAuditoriaService;
        this.eventos = eventos;
        this.trava = trava;
        this.transacao = transacao;
        this.relogio = relogio;
        this.lote = lote;
    }

    @Scheduled(cron = "${anuncio.reagendamento.expiracao.cron}")
    public void executar() {
        Instant agora = relogio.instant();
        List<UUID> candidatas = reagendamentoService.buscarIdsParaExpirar(agora, lote);

        int expiradas = 0;
        for (UUID reagendamentoId : candidatas) {
            try {
                if (Boolean.TRUE.equals(transacao.execute(status -> expirarUma(reagendamentoId, agora)))) {
                    expiradas++;
                }
            } catch (RuntimeException e) {
                log.error("falha ao expirar a proposta de reagendamento {}; as demais continuam", reagendamentoId, e);
            }
        }

        if (!candidatas.isEmpty()) {
            log.info("expiração de reagendamentos: {} expirados de {} candidatos", expiradas, candidatas.size());
        }
    }

    private boolean expirarUma(UUID reagendamentoId, Instant agora) {
        // O anuncioId vem de consulta escalar e a trava vem antes de qualquer leitura da proposta (ver
        // ReagendamentoService.buscarAnuncioId): senão a leitura seguinte devolveria uma cópia velha do contexto.
        Optional<UUID> anuncioId = reagendamentoService.buscarAnuncioId(reagendamentoId);
        if (anuncioId.isEmpty()) {
            return false;
        }
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.get().toString());

        Optional<Reagendamento> vencida = reagendamentoService.buscarPorId(reagendamentoId)
                .filter(proposta -> proposta.estaPendente() && proposta.estaVencida(agora));
        if (vencida.isEmpty()) {
            return false;
        }

        Reagendamento proposta = vencida.get();
        // Anúncio nunca é apagado: ausência aqui é dado corrompido, não cenário de negócio.
        Anuncio anuncio = anuncioService.buscarPorId(anuncioId.get()).orElseThrow(
                () -> new IllegalStateException("anúncio da proposta de reagendamento não existe: " + reagendamentoId));
        UUID prestadorId = aplicacaoService.buscarPrestadorDoServico(anuncioId.get()).orElse(null);

        proposta.expirar(agora);
        reagendamentoService.salvar(proposta);

        Map<String, String> detalhes = new LinkedHashMap<>();
        detalhes.put("anuncioId", anuncioId.get().toString());
        detalhes.put("proponenteId", proposta.getProponenteId().toString());
        detalhes.put("dataVigente", anuncio.getDataHoraPretendida().toString());
        detalhes.put("dataProposta", proposta.getDataProposta().toString());
        detalhes.put("expiraEm", proposta.getExpiraEm().toString());
        logAuditoriaService.registrar("Reagendamento", proposta.getId(), TipoAcaoAuditoria.REAGENDAMENTO_EXPIRADO,
                ORIGEM_AUDITORIA, null, detalhes);
        eventos.publishEvent(new ReagendamentoExpiradoEvent(proposta.getId(), anuncioId.get(), anuncio.getTitulo(),
                anuncio.getContratanteId(), prestadorId, proposta.getProponenteId(), anuncio.getDataHoraPretendida(),
                proposta.getDataProposta(), agora));
        return true;
    }
}
