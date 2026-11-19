package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.limio.api.comum.eventos.AnuncioExpiradoEvent;
import com.limio.api.comum.persistencia.TravaTransacional;
import com.limio.api.comum.persistencia.enums.EscopoTrava;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.AnuncioService;
import com.limio.api.modulos.anuncio.aplicacao.AplicacaoService;

/**
 * Rotina periódica de expiração (ETI-66, UC22 / RF30 / RB09): acha os anúncios vencidos e sem candidatura e expira
 * cada um em transação própria, aberta com {@link TransactionTemplate}. Expirar é {@code ENCERRADO}, sem mexer em
 * candidaturas e nem em cota (sem escolha nunca houve consumo; por isso este usecase não conhece a porta de cota).
 * Publica {@link AnuncioExpiradoEvent} dentro da transação do anúncio, entregue só após o commit (ADR-0003) — o aviso
 * ao Empregador é do módulo {@code notificacao} (US56).
 *
 * O método {@link #executar()} não é transacional: cada anúncio tem a própria transação, e uma falha é logada sem
 * impedir os outros (mesmo padrão de {@code LimparRetencaoUseCase}). O {@code TransactionTemplate} dispensa um segundo
 * bean só para passar pelo proxy do Spring.
 *
 * Idempotente: o filtro de {@link AnuncioService#buscarIdsParaExpirar} é só uma pré-seleção. A decisão é tomada em
 * {@link #expirarUm}, sob a trava do anúncio, com o estado atual — quem chegar depois de outra expiração, de um
 * cancelamento ou de uma candidatura vê o anúncio como ele ficou e sai sem efeito nem evento.
 *
 * Sem lock de job (ShedLock): com várias instâncias rodando, o advisory lock por anúncio e a nova checagem garantem um
 * único encerramento e um único evento. Processa até {@code lote} por execução; sobrando, a execução seguinte continua.
 */
@Service
public class ExpirarAnunciosSemCandidatosUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpirarAnunciosSemCandidatosUseCase.class);

    private final AnuncioService anuncioService;
    private final AplicacaoService aplicacaoService;
    private final ApplicationEventPublisher eventos;
    private final TravaTransacional trava;
    private final TransactionTemplate transacao;
    private final Clock relogio;
    private final int lote;

    public ExpirarAnunciosSemCandidatosUseCase(AnuncioService anuncioService, AplicacaoService aplicacaoService,
            ApplicationEventPublisher eventos, TravaTransacional trava, TransactionTemplate transacao, Clock relogio,
            @Value("${anuncio.expiracao.lote}") int lote) {
        this.anuncioService = anuncioService;
        this.aplicacaoService = aplicacaoService;
        this.eventos = eventos;
        this.trava = trava;
        this.transacao = transacao;
        this.relogio = relogio;
        this.lote = lote;
    }

    @Scheduled(cron = "${anuncio.expiracao.cron}")
    public void executar() {
        Instant agora = relogio.instant();
        List<UUID> candidatos = anuncioService.buscarIdsParaExpirar(agora, lote);

        int expirados = 0;
        for (UUID anuncioId : candidatos) {
            try {
                if (Boolean.TRUE.equals(transacao.execute(status -> expirarUm(anuncioId, agora)))) {
                    expirados++;
                }
            } catch (RuntimeException e) {
                log.error("falha ao expirar o anúncio {}; os demais continuam", anuncioId, e);
            }
        }

        if (!candidatos.isEmpty()) {
            log.info("expiração de anúncios: {} expirados de {} candidatos", expirados, candidatos.size());
        }
    }

    private boolean expirarUm(UUID anuncioId, Instant agora) {
        // Lock antes de ler: candidatura, escolha e cancelamento travam o mesmo escopo (ver EscopoTrava.ANUNCIO).
        trava.travar(EscopoTrava.ANUNCIO, anuncioId.toString());

        Optional<Anuncio> expiravel = anuncioService.buscarPorId(anuncioId).filter(a -> a.permiteExpiracao(agora));
        if (expiravel.isEmpty() || aplicacaoService.existeAlguma(anuncioId)) {
            return false;
        }

        Anuncio anuncio = expiravel.get();
        anuncio.expirar(agora);
        anuncioService.salvar(anuncio);

        eventos.publishEvent(new AnuncioExpiradoEvent(anuncioId, anuncio.getContratanteId(), anuncio.getTitulo(), agora));
        return true;
    }
}
