package com.limio.api.modulos.notificacao.actions.usecase;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.notificacao.Notificacao;
import com.limio.api.modulos.notificacao.NotificacaoService;
import com.limio.api.modulos.notificacao.excecao.NotificacaoNaoEncontradaException;

/**
 * Marca uma notificação como lida ao abri-la (ETI-110). Só a do próprio usuário: a de outra pessoa responde igual a
 * um id inexistente (404). Abrir de novo não muda a data da primeira leitura.
 */
@Service
public class MarcarNotificacaoComoLidaUseCase {

    private final NotificacaoService notificacaoService;
    private final Clock relogio;

    public MarcarNotificacaoComoLidaUseCase(NotificacaoService notificacaoService, Clock relogio) {
        this.notificacaoService = notificacaoService;
        this.relogio = relogio;
    }

    /** @return o total de não lidas depois da marcação, pro sino */
    @Transactional
    public long executar(UUID notificacaoId, UUID usuarioId) {
        Notificacao notificacao = notificacaoService.buscarDoUsuario(notificacaoId, usuarioId)
                .orElseThrow(NotificacaoNaoEncontradaException::new);
        notificacao.marcarComoLida(relogio.instant());
        notificacaoService.salvar(notificacao);
        return notificacaoService.contarNaoLidas(usuarioId);
    }
}
