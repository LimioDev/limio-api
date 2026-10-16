package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.limio.api.modulos.auth.actions.helper.ExecutarAposCommit;

class ExecutarAposCommitTest {

    @AfterEach
    void limparSincronizacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void semTransacaoDeveExecutarNaHora() {
        AtomicInteger chamadas = new AtomicInteger();

        ExecutarAposCommit.executar(chamadas::incrementAndGet);

        assertThat(chamadas).hasValue(1);
    }

    @Test
    void comTransacaoSoDeveExecutarNoCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger chamadas = new AtomicInteger();

        ExecutarAposCommit.executar(chamadas::incrementAndGet);
        assertThat(chamadas).hasValue(0);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(chamadas).hasValue(1);
    }

    @Test
    void comRollbackNaoDeveExecutar() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger chamadas = new AtomicInteger();

        ExecutarAposCommit.executar(chamadas::incrementAndGet);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(chamadas).hasValue(0);
    }

    @Test
    void excecaoDaAcaoNaoDevePropagar() {
        TransactionSynchronizationManager.initSynchronization();
        ExecutarAposCommit.executar(() -> {
            throw new IllegalStateException("provedor fora do ar");
        });

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }
}
