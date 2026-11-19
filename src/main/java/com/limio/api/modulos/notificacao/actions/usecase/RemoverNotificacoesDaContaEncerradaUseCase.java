package com.limio.api.modulos.notificacao.actions.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.modulos.notificacao.NotificacaoService;

/**
 * Reação de {@code notificacao} ao encerramento de conta (LGPD), igual à preferência: apaga as notificações do
 * usuário, que sem FK pra {@code usuario} (ADR-0001 §1.6) ficariam órfãs pra sempre. Roda depois do commit de
 * {@code auth}, em transação própria; falha é logada e não volta pra quem encerrou a conta.
 */
@Service
public class RemoverNotificacoesDaContaEncerradaUseCase {

    private static final Logger log = LoggerFactory.getLogger(RemoverNotificacoesDaContaEncerradaUseCase.class);

    private final NotificacaoService notificacaoService;

    public RemoverNotificacoesDaContaEncerradaUseCase(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    // REQUIRES_NEW: no AFTER_COMMIT a transação de auth já terminou (mesmo motivo do listener da preferência).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEncerrarConta(ContaEncerradaEvent evento) {
        try {
            notificacaoService.apagarDoUsuario(evento.usuarioId());
        } catch (RuntimeException e) {
            log.error("falha ao apagar as notificações da conta encerrada {}", evento.usuarioId(), e);
        }
    }
}
