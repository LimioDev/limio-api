package com.limio.api.comum.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio da aplicação. Regra que depende do "agora" de um jeito que o teste precisa controlar (ex.: virada do mês
 * da cota, ETI-79) recebe este {@link Clock} em vez de chamar {@code Instant.now()}; o story troca o bean.
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
