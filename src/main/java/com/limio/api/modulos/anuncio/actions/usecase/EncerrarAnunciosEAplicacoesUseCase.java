package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.AvisoDeAnuncioEvent;
import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
import com.limio.api.modulos.anuncio.enums.StatusAplicacao;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/**
 * UC16 — reação de {@code anuncio} ao encerramento de conta (TICKET-0049).
 * Escuta {@link ContaEncerradaEvent} depois que {@code auth} confirma a
 * transação ({@code AFTER_COMMIT}): se a conta já está encerrada, cada
 * anúncio/candidatura é processado isoladamente (ver {@link
 * #processarComIsolamento}) — uma falha num item não desfaz os demais.
 *
 * Dois papéis possíveis pro usuário que saiu: contratante (dono de {@code
 * Anuncio}) e/ou prestador (dono de {@code Aplicacao}) — processa os dois.
 */
@Service
public class EncerrarAnunciosEAplicacoesUseCase {

    private static final Logger log = LoggerFactory.getLogger(EncerrarAnunciosEAplicacoesUseCase.class);

    private static final Duration JANELA_COMBINADO_PROXIMO = Duration.ofHours(24);
    private static final String MOTIVO_SAIDA_DE_CONTA = "Cancelado por saída de conta";

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ReagendamentoService reagendamentoService;
    private final LogAuditoriaService logAuditoriaService;
    private final ApplicationEventPublisher eventPublisher;

    public EncerrarAnunciosEAplicacoesUseCase(AnuncioService anuncioService,
            AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService,
            LogAuditoriaService logAuditoriaService,
            ApplicationEventPublisher eventPublisher) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.logAuditoriaService = logAuditoriaService;
        this.eventPublisher = eventPublisher;
    }

    // REQUIRES_NEW: no AFTER_COMMIT já não há transação ativa pra participar (a de auth já comitou) —
    // REQUIRED (padrão) falha a subida do contexto exatamente por causa disso.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEncerrarConta(ContaEncerradaEvent evento) {
        Instant agora = Instant.now();
        UUID usuarioId = evento.usuarioId();

        encerrarAnunciosDoContratante(usuarioId, agora);

        aplicacaoService.buscarAbertasDoPrestador(usuarioId)
                .forEach(aplicacao -> processarComIsolamento("Aplicacao", aplicacao.getId(),
                        () -> encerrarAplicacaoDoPrestador(aplicacao, agora)));
    }

    /**
     * Anúncios abertos do contratante que saiu: busca candidaturas e
     * reagendamentos pendentes em lote (1 query cada pra todos os anúncios,
     * em vez de 2 por anúncio) e só então processa anúncio por anúncio.
     */
    private void encerrarAnunciosDoContratante(UUID usuarioId, Instant agora) {
        List<Anuncio> anuncios = anuncioService.buscarAbertosDoContratante(usuarioId);
        if (anuncios.isEmpty()) {
            return;
        }

        List<UUID> anuncioIds = anuncios.stream().map(Anuncio::getId).toList();
        Map<UUID, List<Aplicacao>> candidaturasPorAnuncio = aplicacaoService.buscarAbertasDosAnuncios(anuncioIds)
                .stream()
                .collect(Collectors.groupingBy(Aplicacao::getAnuncioId));
        cancelarReagendamentosPendentes(anuncioIds, agora);

        for (Anuncio anuncio : anuncios) {
            List<Aplicacao> candidaturasAbertas = candidaturasPorAnuncio.getOrDefault(anuncio.getId(), List.of());
            processarComIsolamento("Anuncio", anuncio.getId(),
                    () -> encerrarAnuncioDoContratante(anuncio, candidaturasAbertas, agora));
        }
    }

    /** Anúncio do contratante que saiu: encerra e encerra/avisa toda candidatura aberta (não só a escolhida). */
    private void encerrarAnuncioDoContratante(Anuncio anuncio, List<Aplicacao> candidaturasAbertas, Instant agora) {
        boolean combinadoProximo = anuncio.estaAMenosDe(JANELA_COMBINADO_PROXIMO, agora);

        anuncio.encerrarPorExclusaoDeConta(agora);
        anuncioService.salvar(anuncio);

        for (Aplicacao aplicacao : candidaturasAbertas) {
            aplicacao.encerrar(agora, MOTIVO_SAIDA_DE_CONTA);
            aplicacaoService.salvar(aplicacao);
            avisarEncerramento(aplicacao.getPrestadorId(), anuncio.getId(), combinadoProximo, agora, "Anúncio encerrado");
        }
    }

    /**
     * Candidatura do prestador que saiu: encerra sempre. Se ela estava
     * ESCOLHIDA ou AGUARDANDO_VALIDACAO (prestador combinado pra um anúncio
     * de outro contratante, antes ou depois do check-in), o anúncio e as
     * outras candidaturas abertas dele também encerram — não tem mais quem
     * cumpra o serviço combinado. Só a janela de 24h muda: dentro dela é
     * "impossibilidade operacional" (auditoria + aviso específico); fora
     * dela é só "candidatura encerrada".
     */
    private void encerrarAplicacaoDoPrestador(Aplicacao aplicacao, Instant agora) {
        boolean fechaAnuncio = fechaAnuncioLigado(aplicacao.getStatus());

        aplicacao.encerrar(agora, MOTIVO_SAIDA_DE_CONTA);
        aplicacaoService.salvar(aplicacao);

        if (!fechaAnuncio) {
            return;
        }

        Optional<Anuncio> anuncioEncontrado = anuncioService.buscarPorId(aplicacao.getAnuncioId());
        if (anuncioEncontrado.isEmpty()) {
            log.warn("Aplicacao {} referencia Anuncio {} inexistente ao encerrar conta; candidatura encerrada, "
                    + "anúncio pulado", aplicacao.getId(), aplicacao.getAnuncioId());
            return;
        }

        Anuncio anuncio = anuncioEncontrado.get();
        if (!anuncio.isAberto()) {
            return;
        }
        boolean combinadoProximo = anuncio.estaAMenosDe(JANELA_COMBINADO_PROXIMO, agora);

        anuncio.encerrarPorExclusaoDeConta(agora);
        anuncioService.salvar(anuncio);
        cancelarReagendamentosPendentes(List.of(anuncio.getId()), agora);

        for (Aplicacao outra : aplicacaoService.buscarAbertasDoAnuncio(anuncio.getId())) {
            if (Objects.equals(outra.getId(), aplicacao.getId())) {
                continue;
            }
            outra.encerrar(agora, MOTIVO_SAIDA_DE_CONTA);
            aplicacaoService.salvar(outra);
            avisarEncerramento(outra.getPrestadorId(), anuncio.getId(), combinadoProximo, agora, "Candidatura encerrada");
        }

        avisarEncerramento(anuncio.getContratanteId(), anuncio.getId(), combinadoProximo, agora, "Candidatura encerrada");
    }

    private static boolean fechaAnuncioLigado(StatusAplicacao status) {
        return status == StatusAplicacao.ESCOLHIDA || status == StatusAplicacao.AGUARDANDO_VALIDACAO;
    }

    /** Combinado <24h: auditoria + aviso de impossibilidade operacional. Fora da janela: só o aviso padrão. */
    private void avisarEncerramento(UUID destinatarioId, UUID anuncioId, boolean combinadoProximo, Instant agora,
            String assuntoPadrao) {
        if (combinadoProximo) {
            registrarCombinadoCancelado(anuncioId);
            avisar(destinatarioId, anuncioId, "Combinado cancelado por impossibilidade operacional", agora);
        } else {
            avisar(destinatarioId, anuncioId, assuntoPadrao, agora);
        }
    }

    private void cancelarReagendamentosPendentes(List<UUID> anuncioIds, Instant agora) {
        for (Reagendamento reagendamento : reagendamentoService.buscarPendentesDosAnuncios(anuncioIds)) {
            reagendamento.cancelarSemEfeito(agora);
            reagendamentoService.salvar(reagendamento);
        }
    }

    /** Cota não é estornada: ausência de código aqui (nenhuma chamada a serviço de cota/assinatura) já é a regra. */
    private void registrarCombinadoCancelado(UUID anuncioId) {
        logAuditoriaService.registrar("Anuncio", anuncioId, TipoAcaoAuditoria.COMBINADO_CANCELADO,
                "anuncio-encerrar-conta", null);
    }

    private void avisar(UUID destinatarioId, UUID anuncioId, String assunto, Instant agora) {
        eventPublisher.publishEvent(new AvisoDeAnuncioEvent(destinatarioId, anuncioId, assunto, agora));
    }

    /**
     * Roda {@code acao} isolada: uma exceção aqui (ex.: FK crua quebrada,
     * violação de índice) é logada e pulada, sem propagar — a transação
     * REQUIRES_NEW do listener continua válida pros demais itens em vez de
     * ser marcada rollback-only por um item ruim.
     */
    private void processarComIsolamento(String tipoEntidade, UUID entidadeId, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException ex) {
            log.error("Falha ao processar {} {} no encerramento de conta; item pulado, demais continuam",
                    tipoEntidade, entidadeId, ex);
        }
    }
}
