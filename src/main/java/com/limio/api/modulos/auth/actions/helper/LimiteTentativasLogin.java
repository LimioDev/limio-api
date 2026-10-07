package com.limio.api.modulos.auth.actions.helper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Rate limit do login: {@code maxFalhas} falhas para o mesmo e-mail dentro de
 * {@code janela} bloqueiam o login por {@code bloqueio}, contado a partir da
 * última falha. Durante o bloqueio a senha nem é conferida, então nenhuma
 * falha nova é registrada e o bloqueio não se estende sozinho.
 */
@Component
public class LimiteTentativasLogin {

    private final int maxFalhas;
    private final Duration janela;
    private final Duration bloqueio;

    public LimiteTentativasLogin(@Value("${auth.login.max-falhas}") int maxFalhas,
            @Value("${auth.login.janela-falhas}") Duration janela,
            @Value("${auth.login.bloqueio}") Duration bloqueio) {
        if (maxFalhas < 1) {
            throw new IllegalArgumentException("auth.login.max-falhas precisa ser ao menos 1");
        }
        this.maxFalhas = maxFalhas;
        this.janela = janela;
        this.bloqueio = bloqueio;
    }

    /** Quantas falhas recentes {@link #estaBloqueado} precisa receber. */
    public int maxFalhas() {
        return maxFalhas;
    }

    /** @param falhasRecentes instantes das falhas do e-mail, da mais recente pra mais antiga */
    public boolean estaBloqueado(List<Instant> falhasRecentes, Instant agora) {
        if (falhasRecentes.size() < maxFalhas) {
            return false;
        }
        Instant ultima = falhasRecentes.get(0);
        Instant enesima = falhasRecentes.get(maxFalhas - 1);
        boolean estourouNaJanela = !enesima.isBefore(ultima.minus(janela));
        return estourouNaJanela && agora.isBefore(ultima.plus(bloqueio));
    }
}
