package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.comum.auditoria.LogAuditoriaService;
import com.limio.api.comum.auditoria.enums.TipoAcaoAuditoria;
import com.limio.api.comum.eventos.AvisoDeAnuncioEvent;
import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;
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
    private final TravaTransacional trava;
    private final TransactionTemplate transacao;

    public EncerrarAnunciosEAplicacoesUseCase(AnuncioService anuncioService,
            AplicacaoService aplicacaoService,
            ReagendamentoService reagendamentoService,
            LogAuditoriaService logAuditoriaService,
            ApplicationEventPublisher eventPublisher,
            TravaTransacional trava,
            PlatformTransactionManager gerenteTransacao) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.reagendamentoService = reagendamentoService;
        this.logAuditoriaService = logAuditoriaService;
        this.eventPublisher = eventPublisher;
        this.trava = trava;
        // REQUIRES_NEW: no AFTER_COMMIT a transação de auth já comitou mas ainda está vinculada à thread; participar
        // dela (REQUIRED) gravaria sem nunca commitar.
        this.transacao = new TransactionTemplate(gerenteTransacao);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Sem @Transactional no método: cada anúncio/candidatura roda na própria transação (ver processarComIsolamento).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoEncerrarConta(ContaEncerradaEvent evento) {
        Instant agora = Instant.now();
        UUID usuarioId = evento.usuarioId();

        // Só ids aqui (fora de qualquer transação de item): cada item é carregado depois da trava do anúncio, pra não
        // trabalhar numa cópia velha (sem @Version, gravar a cópia velha sobrescreveria o que outra transação commitou).
        anuncioService.buscarIdsAbertosDoContratante(usuarioId)
                .forEach(anuncioId -> processarComIsolamento("Anuncio", anuncioId,
                        () -> encerrarAnuncioDoContratante(anuncioId, agora)));

        aplicacaoService.buscarIdsAbertasDoPrestador(usuarioId)
                .forEach(aplicacaoId -> processarComIsolamento("Aplicacao", aplicacaoId,
                        () -> encerrarAplicacaoDoPrestador(aplicacaoId, agora)));
    }

    /**
     * Anúncio do contratante que saiu: toma a trava e só então lê o anúncio e as candidaturas abertas, encerra e
     * encerra/avisa toda candidatura aberta (não só a escolhida). Se o anúncio já fechou enquanto isso, pula.
     */
    private void encerrarAnuncioDoContratante(UUID anuncioId, Instant agora) {
        travarAnuncio(anuncioId);

        Optional<Anuncio> anuncioEncontrado = anuncioService.buscarPorId(anuncioId);
        if (anuncioEncontrado.isEmpty() || !anuncioEncontrado.get().isAberto()) {
            return;
        }
        Anuncio anuncio = anuncioEncontrado.get();
        boolean combinadoProximo = anuncio.estaAMenosDe(JANELA_COMBINADO_PROXIMO, agora);

        anuncio.encerrarPorExclusaoDeConta(agora);
        anuncioService.salvar(anuncio);
        reagendamentoService.encerrarPendentesSemEfeito(List.of(anuncio.getId()), agora);

        for (Aplicacao aplicacao : aplicacaoService.buscarAbertasDoAnuncio(anuncioId)) {
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
     *
     * O status só é lido depois da trava do anúncio (que é tomada sempre: o status lido antes dela poderia estar
     * velho). A trava vem antes de qualquer escrita: propor/responder tomam a trava e depois a linha da candidatura.
     */
    private void encerrarAplicacaoDoPrestador(UUID aplicacaoId, Instant agora) {
        Optional<UUID> anuncioId = aplicacaoService.buscarAnuncioId(aplicacaoId);
        if (anuncioId.isEmpty()) {
            return;
        }
        travarAnuncio(anuncioId.get());

        Optional<Aplicacao> encontrada = aplicacaoService.buscarPorId(aplicacaoId).filter(Aplicacao::isAberta);
        if (encontrada.isEmpty()) {
            return;
        }
        Aplicacao aplicacao = encontrada.get();
        boolean fechaAnuncio = aplicacao.isEscolhida();

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
        reagendamentoService.encerrarPendentesSemEfeito(List.of(anuncio.getId()), agora);

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

    /**
     * Mesma trava {@code ANUNCIO} que propor, responder, cancelar e os demais fluxos de reagendamento tomam como
     * primeira instrução: sem ela, um aceite concorrente seria sobrescrito por {@code SEM_EFEITO} (não há
     * {@code @Version}) e uma proposta nova poderia sobrar pendente num anúncio já encerrado. Cada item roda em
     * transação própria (ver {@link #processarComIsolamento}), então a trava é tomada antes de reler o item e solta no
     * commit (ou rollback) dele — nunca fica presa até o fim do encerramento da conta.
     */
    private void travarAnuncio(UUID anuncioId) {
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());
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

    /** Cota não é estornada: ausência de código aqui (nenhuma chamada a serviço de cota/assinatura) já é a regra. */
    private void registrarCombinadoCancelado(UUID anuncioId) {
        logAuditoriaService.registrar("Anuncio", anuncioId, TipoAcaoAuditoria.COMBINADO_CANCELADO,
                "anuncio-encerrar-conta", null);
    }

    private void avisar(UUID destinatarioId, UUID anuncioId, String assunto, Instant agora) {
        eventPublisher.publishEvent(new AvisoDeAnuncioEvent(destinatarioId, anuncioId, assunto, agora));
    }

    /**
     * Roda {@code acao} numa transação própria ({@code REQUIRES_NEW}): o commit de um item solta a trava dele, e um erro
     * de banco (FK crua quebrada, violação de índice, deadlock com outra conta encerrada ao mesmo tempo) desfaz só
     * esse item — é logado e pulado, sem abortar a transação dos demais.
     */
    private void processarComIsolamento(String tipoEntidade, UUID entidadeId, Runnable acao) {
        try {
            transacao.executeWithoutResult(status -> acao.run());
        } catch (RuntimeException ex) {
            log.error("Falha ao processar {} {} no encerramento de conta; item pulado, demais continuam",
                    tipoEntidade, entidadeId, ex);
        }
    }
}
