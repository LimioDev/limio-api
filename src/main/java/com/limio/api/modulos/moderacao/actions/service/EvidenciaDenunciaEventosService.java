package com.limio.api.modulos.moderacao.actions.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.modulos.moderacao.eventos.EvidenciaDenunciaEnviadaEvent;

/**
 * Limpeza do storage quando o registro da denúncia é desfeito depois do upload da foto. Sem
 * {@code fallbackExecution}: fora de transação o evento é descartado (o usecase que publica é {@code @Transactional}).
 */
@Component
public class EvidenciaDenunciaEventosService {

    private final ArmazenamentoService armazenamentoService;

    public EvidenciaDenunciaEventosService(ArmazenamentoService armazenamentoService) {
        this.armazenamentoService = armazenamentoService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void aoDesfazerRegistro(EvidenciaDenunciaEnviadaEvent evento) {
        armazenamentoService.remover(evento.chave());
    }
}
