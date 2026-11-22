package com.limio.api.modulos.moderacao.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Severidade mostrada na fila de denúncias (ETI-116). Sai do motivo: não é gravada nem escolhida por ninguém. A
 * tabela é provisória (pendência com o PO): fraude e falta de pagamento são as mais graves, porque envolvem dinheiro.
 */
public enum Severidade {
    ALTA,
    MEDIA,
    BAIXA;

    public static Severidade de(MotivoDenuncia motivo) {
        return switch (motivo) {
            case SUSPEITA_DE_FRAUDE, NAO_PAGOU_VALOR_COMBINADO -> ALTA;
            case SERVICO_NAO_REALIZADO, COMPORTAMENTO_INADEQUADO -> MEDIA;
            case OUTRO_MOTIVO -> BAIXA;
        };
    }

    /** Os motivos que caem nesta severidade, para filtrar a fila. */
    public List<MotivoDenuncia> motivos() {
        return Arrays.stream(MotivoDenuncia.values()).filter(motivo -> de(motivo) == this).toList();
    }
}
