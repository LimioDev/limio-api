package com.limio.api.modulos.notificacao.actions.usecase;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.notificacao.NotificacaoService;

/**
 * "Marcar lidas" da tela 43 (ETI-110): preenche {@code lidaEm} em todas as não lidas do usuário, num update só. As já
 * lidas ficam com a data que tinham. Sem nada pra marcar, não é erro.
 */
@Service
public class MarcarTodasNotificacoesComoLidasUseCase {

    private final NotificacaoService notificacaoService;
    private final Clock relogio;

    public MarcarTodasNotificacoesComoLidasUseCase(NotificacaoService notificacaoService, Clock relogio) {
        this.notificacaoService = notificacaoService;
        this.relogio = relogio;
    }

    /** @return o total de não lidas depois da marcação, pro sino */
    @Transactional
    public long executar(UUID usuarioId) {
        notificacaoService.marcarTodasComoLidas(usuarioId, relogio.instant());
        return notificacaoService.contarNaoLidas(usuarioId);
    }
}
