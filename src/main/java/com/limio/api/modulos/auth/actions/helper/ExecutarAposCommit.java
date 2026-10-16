package com.limio.api.modulos.auth.actions.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class ExecutarAposCommit {

    private static final Logger log = LoggerFactory.getLogger(ExecutarAposCommit.class);

    private ExecutarAposCommit() {
    }

    public static void executar(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rodar(acao);
                }
            });
        } else {
            rodar(acao);
        }
    }

    private static void rodar(Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException e) {
            log.error("falha ao executar ação pós-commit", e);
        }
    }
}
