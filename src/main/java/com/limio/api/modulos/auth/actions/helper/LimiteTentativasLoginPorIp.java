package com.limio.api.modulos.auth.actions.helper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rate limit do login por IP (decisão do tech lead, 2026-10-26; pendência do TICKET-0032): sem ele, um
 * atacante testa uma senha em muitos e-mails sem bater no limite por e-mail. Mesma regra do
 * {@link LimiteTentativasLogin}, com limite próprio e mais alto, porque vários usuários podem dividir o
 * mesmo IP (rede da escola, operadora com NAT).
 */
@Component
public class LimiteTentativasLoginPorIp {

    private final LimiteTentativasLogin regra;

    public LimiteTentativasLoginPorIp(@Value("${auth.login.max-falhas-por-ip}") int maxFalhas,
            @Value("${auth.login.janela-falhas}") Duration janela,
            @Value("${auth.login.bloqueio}") Duration bloqueio) {
        this.regra = new LimiteTentativasLogin(maxFalhas, janela, bloqueio);
    }

    public int maxFalhas() {
        return regra.maxFalhas();
    }

    public boolean estaBloqueado(List<Instant> falhasRecentes, Instant agora) {
        return regra.estaBloqueado(falhasRecentes, agora);
    }
}
