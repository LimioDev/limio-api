package com.limio.api.modulos.moderacao.actions.helper;

import java.time.Duration;
import java.time.Instant;

/** Janela de avaliação (ETI-107): 7 dias corridos depois da conclusão do serviço. */
public final class JanelaAvaliacaoHelper {

    public static final Duration DURACAO = Duration.ofDays(7);

    private JanelaAvaliacaoHelper() {
    }

    /** Último instante, exclusivo, em que ainda dá pra avaliar. */
    public static Instant avaliarAte(Instant concluidoEm) {
        return concluidoEm.plus(DURACAO);
    }

    public static boolean aberta(Instant concluidoEm, Instant agora) {
        return agora.isBefore(avaliarAte(concluidoEm));
    }
}
