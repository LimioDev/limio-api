package com.limio.api.comum.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Execução assíncrona (ETI-112). Hoje só o envio dos avisos usa: chamar Expo e Resend dentro da requisição que gerou
 * o aviso deixaria a publicação de um anúncio esperando um push por Prestador compatível.
 *
 * <p>Com {@code notificacao.envio.assincrono=false} o executor roda na própria thread: os testes conferem o envio sem
 * esperar outra thread.
 *
 * <p>Fila cheia não descarta o envio: quem publicou roda o envio na própria thread ({@code CallerRunsPolicy}). Sem
 * isso o executor lança {@code RejectedExecutionException} dentro do ouvinte, o Spring só loga, e o aviso fica sem
 * envio e sem linha em {@code envio_notificacao}. A ação que gerou o aviso já foi commitada nesse ponto; só a resposta
 * dela atrasa, e só sob carga.
 */
@Configuration
@EnableAsync
public class AssincronoConfig {

    /** Nome do executor do envio dos avisos, pra {@code @Async(AssincronoConfig.ENVIO_DE_AVISOS)}. */
    public static final String ENVIO_DE_AVISOS = "envioDeAvisos";

    @Bean(name = ENVIO_DE_AVISOS)
    public Executor envioDeAvisos(@Value("${notificacao.envio.assincrono}") boolean assincrono,
            @Value("${notificacao.envio.threads}") int threads,
            @Value("${notificacao.envio.fila}") int fila) {
        if (!assincrono) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("envio-avisos-");
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(fila);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
