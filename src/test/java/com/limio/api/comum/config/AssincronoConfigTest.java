package com.limio.api.comum.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Executor do envio dos avisos (ETI-112) com thread de verdade, não o síncrono dos stories. */
class AssincronoConfigTest {

    @Test
    void dadoAssincronoDesligado_entaoRodaNaPropriaThread() {
        assertThat(new AssincronoConfig().envioDeAvisos(false, 1, 1)).isInstanceOf(SyncTaskExecutor.class);
    }

    @Test
    void dadoFilaCheia_quandoChegaMaisUmEnvio_entaoRodaNaThreadDeQuemPublicouEmVezDeDescartar() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AssincronoConfig().envioDeAvisos(true, 1, 1);
        CountDownLatch liberar = new CountDownLatch(1);
        CountDownLatch ocupado = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                ocupado.countDown();
                aguardar(liberar);
            });
            ocupado.await(5, TimeUnit.SECONDS);
            executor.execute(() -> aguardar(liberar));

            AtomicReference<String> thread = new AtomicReference<>();
            executor.execute(() -> thread.set(Thread.currentThread().getName()));

            assertThat(thread.get()).isEqualTo(Thread.currentThread().getName());
        } finally {
            liberar.countDown();
            executor.shutdown();
        }
    }

    @Test
    void dadoAssincronoLigado_quandoEnvia_entaoRodaNaThreadDoEnvio() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AssincronoConfig().envioDeAvisos(true, 1, 10);
        AtomicReference<String> thread = new AtomicReference<>();
        CountDownLatch rodou = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                thread.set(Thread.currentThread().getName());
                rodou.countDown();
            });

            assertThat(rodou.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(thread.get()).startsWith("envio-avisos-");
        } finally {
            executor.shutdown();
        }
    }

    private static void aguardar(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
