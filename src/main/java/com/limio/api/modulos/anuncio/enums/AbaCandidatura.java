package com.limio.api.modulos.anuncio.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Abas da tela "Aplicações" do Prestador (ETI-77). Cada {@link StatusAplicacao} cai em exatamente uma aba —
 * {@link #de} é a fonte única e o {@code switch} sem {@code default} obriga a classificar status novo.
 */
public enum AbaCandidatura {

    /** Aguardando a escolha do Empregador. */
    PENDENTES,

    /** Prestador escolhido, serviço ainda em andamento. */
    COMBINADAS,

    /** Serviço encerrado pra esta candidatura: recusada, concluída ou encerrada. */
    HISTORICO;

    public static AbaCandidatura de(StatusAplicacao status) {
        return switch (status) {
            case ENVIADA -> PENDENTES;
            case ESCOLHIDA, AGUARDANDO_VALIDACAO -> COMBINADAS;
            case RECUSADA, CONCLUIDA, ENCERRADA -> HISTORICO;
        };
    }

    public List<StatusAplicacao> status() {
        return Arrays.stream(StatusAplicacao.values()).filter(status -> de(status) == this).toList();
    }
}
