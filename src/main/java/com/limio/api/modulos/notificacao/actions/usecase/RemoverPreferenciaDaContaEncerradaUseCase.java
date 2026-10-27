package com.limio.api.modulos.notificacao.actions.usecase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.eventos.ContaEncerradaEvent;
import com.limio.api.modulos.notificacao.preferencianotificacao.PreferenciaNotificacaoService;

/**
 * Reação de {@code notificacao} ao encerramento de conta (UC15 item 8, LGPD): apaga a preferência do
 * usuário, que sem FK pra {@code usuario} (ADR-0001 §1.6) ficaria órfã pra sempre. Roda depois do commit
 * de {@code auth}, em transação própria; falha é logada e não volta pra quem encerrou a conta.
 */
@Service
public class RemoverPreferenciaDaContaEncerradaUseCase {

    private static final Logger log = LoggerFactory.getLogger(RemoverPreferenciaDaContaEncerradaUseCase.class);

    private final PreferenciaNotificacaoService preferenciaNotificacaoService;

    public RemoverPreferenciaDaContaEncerradaUseCase(PreferenciaNotificacaoService preferenciaNotificacaoService) {
        this.preferenciaNotificacaoService = preferenciaNotificacaoService;
    }

    // REQUIRES_NEW: no AFTER_COMMIT a transação de auth já terminou (mesmo motivo do listener de anuncio).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoEncerrarConta(ContaEncerradaEvent evento) {
        try {
            preferenciaNotificacaoService.apagarDoUsuario(evento.usuarioId());
        } catch (RuntimeException e) {
            log.error("falha ao apagar a preferência de notificação da conta encerrada {}", evento.usuarioId(), e);
        }
    }
}
