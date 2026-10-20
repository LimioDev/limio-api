package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
 * transação ({@code AFTER_COMMIT}): se o listener falhar aqui, a conta já
 * está encerrada (isso não é desfeito) e os registros deste módulo ficam
 * pendentes de reconciliação — risco aceito, fora de escopo deste card.
 *
 * Dois papéis possíveis pro usuário que saiu: contratante (dono de {@code
 * Anuncio}) e/ou prestador (dono de {@code Aplicacao}) — processa os dois.
 */
@Service
public class EncerrarAnunciosEAplicacoesUseCase {

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

    // REQUIRES_NEW: no AFTER_COMMIT já não há transação ativa pra "participar" (a de auth já comitou) —
    // REQUIRED (padrão) falha a subida do contexto exatamente por causa disso.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEncerrarConta(ContaEncerradaEvent evento) {
        Instant agora = Instant.now();
        UUID usuarioId = evento.usuarioId();

        anuncioService.buscarAbertosDoContratante(usuarioId)
                .forEach(anuncio -> encerrarAnuncioDoContratante(anuncio, agora));

        aplicacaoService.buscarAbertasDoPrestador(usuarioId)
                .forEach(aplicacao -> encerrarAplicacaoDoPrestador(aplicacao, agora));
    }

    /** Anúncio do contratante que saiu: encerra, cancela reagendamento pendente, encerra e avisa o candidato escolhido. */
    private void encerrarAnuncioDoContratante(Anuncio anuncio, Instant agora) {
        boolean combinadoProximo = anuncio.estaAMenosDe(JANELA_COMBINADO_PROXIMO, agora);
        Optional<Aplicacao> escolhida = aplicacaoService.buscarEscolhidaDoAnuncio(anuncio.getId());

        anuncio.encerrarPorExclusaoDeConta(agora);
        anuncioService.salvar(anuncio);
        cancelarReagendamentosPendentes(anuncio.getId(), agora);

        escolhida.ifPresent(aplicacao -> {
            aplicacao.encerrar(agora, MOTIVO_SAIDA_DE_CONTA);
            aplicacaoService.salvar(aplicacao);
            avisarEncerramento(aplicacao.getPrestadorId(), anuncio.getId(), combinadoProximo, agora, "Anúncio encerrado");
        });
    }

    /**
     * Candidatura do prestador que saiu: encerra sempre. Se ela estava
     * ESCOLHIDA (prestador combinado pra um anúncio de outro contratante),
     * o anúncio também encerra — não tem mais quem cumpra o serviço
     * combinado, esteja a data perto ou longe. Só a janela de 24h muda:
     * dentro dela é "impossibilidade operacional" (auditoria + aviso
     * específico); fora dela é só "candidatura encerrada".
     */
    private void encerrarAplicacaoDoPrestador(Aplicacao aplicacao, Instant agora) {
        boolean eraEscolhida = aplicacao.getStatus() == StatusAplicacao.ESCOLHIDA;

        aplicacao.encerrar(agora, MOTIVO_SAIDA_DE_CONTA);
        aplicacaoService.salvar(aplicacao);

        if (!eraEscolhida) {
            return;
        }

        Anuncio anuncio = anuncioService.buscarPorIdOuFalhar(aplicacao.getAnuncioId());
        if (!anuncio.isAberto()) {
            return;
        }
        boolean combinadoProximo = anuncio.estaAMenosDe(JANELA_COMBINADO_PROXIMO, agora);

        anuncio.encerrarPorExclusaoDeConta(agora);
        anuncioService.salvar(anuncio);
        cancelarReagendamentosPendentes(anuncio.getId(), agora);

        avisarEncerramento(anuncio.getContratanteId(), anuncio.getId(), combinadoProximo, agora, "Candidatura encerrada");
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

    private void cancelarReagendamentosPendentes(UUID anuncioId, Instant agora) {
        for (Reagendamento reagendamento : reagendamentoService.buscarPendentesDosAnuncios(List.of(anuncioId))) {
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
}
