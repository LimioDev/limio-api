package com.limio.api.modulos.anuncio.actions.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.limio.api.modulos.anuncio.AnuncioService;

/**
 * Rotina periódica de expiração (ETI-66, RF30): acha os anúncios vencidos e sem candidatura e pede a expiração de
 * cada um ao {@link ExpirarAnuncioUseCase}. Não é transacional: cada anúncio tem a própria transação, e uma falha é
 * logada sem impedir os outros (mesmo padrão de {@code LimparRetencaoUseCase}).
 *
 * Sem lock de job (ShedLock): com várias instâncias rodando, o advisory lock por anúncio e a nova checagem de
 * {@link ExpirarAnuncioUseCase} garantem um único encerramento e um único evento. Processa até {@code lote} por
 * execução; sobrando, a execução seguinte continua.
 */
@Service
public class ExpirarAnunciosSemCandidatosUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpirarAnunciosSemCandidatosUseCase.class);

    private final AnuncioService anuncioService;
    private final ExpirarAnuncioUseCase expirarAnuncio;
    private final Clock relogio;
    private final int lote;

    public ExpirarAnunciosSemCandidatosUseCase(AnuncioService anuncioService, ExpirarAnuncioUseCase expirarAnuncio,
            Clock relogio, @Value("${anuncio.expiracao.lote}") int lote) {
        this.anuncioService = anuncioService;
        this.expirarAnuncio = expirarAnuncio;
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
                if (expirarAnuncio.executar(anuncioId, agora)) {
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
}
