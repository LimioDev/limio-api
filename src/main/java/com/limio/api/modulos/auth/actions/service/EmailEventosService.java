package com.limio.api.modulos.auth.actions.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.modulos.auth.eventos.ConfirmacaoEmailSolicitadaEvent;
import com.limio.api.modulos.auth.eventos.EmailContaAlteradoEvent;
import com.limio.api.modulos.auth.eventos.RecuperacaoSenhaSolicitadaEvent;
import com.limio.api.modulos.auth.eventos.SenhaAlteradaEvent;

/**
 * Envia os e-mails transacionais do {@code auth} só depois do commit. Sem
 * {@code fallbackExecution}: fora de uma transação o evento é descartado, nunca
 * envia antes de persistir (os usecases que publicam são {@code @Transactional}).
 * Exceção de um envio é logada e não propaga: em AFTER_COMMIT ela subiria para
 * quem commitou, e um envio não pode derrubar o outro.
 */
@Component
public class EmailEventosService {

    private static final Logger log = LoggerFactory.getLogger(EmailEventosService.class);

    private final EmailService emailService;

    public EmailEventosService(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoSolicitarConfirmacao(ConfirmacaoEmailSolicitadaEvent evento) {
        try {
            emailService.enviarConfirmacaoEmail(evento.destinatario(), evento.link());
        } catch (RuntimeException e) {
            log.error("falha ao enviar confirmação de e-mail pós-commit", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoSolicitarRecuperacao(RecuperacaoSenhaSolicitadaEvent evento) {
        try {
            emailService.enviarRecuperacaoSenha(evento.destinatario(), evento.link());
        } catch (RuntimeException e) {
            log.error("falha ao enviar recuperação de senha pós-commit", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAlterarSenha(SenhaAlteradaEvent evento) {
        try {
            emailService.enviarSenhaAlterada(evento.destinatario());
        } catch (RuntimeException e) {
            log.error("falha ao avisar a alteração de senha pós-commit", e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAlterarEmail(EmailContaAlteradoEvent evento) {
        try {
            emailService.enviarEmailAlterado(evento.emailAntigo(), evento.novoEmail());
        } catch (RuntimeException e) {
            log.error("falha ao avisar o e-mail antigo pós-commit", e);
        }
    }
}
