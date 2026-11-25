package com.limio.api.modulos.moderacao.enums;

/** Motivos da denúncia (UC "Registrar denúncia", tela 46). O texto exibido fica no app. */
public enum MotivoDenuncia {
    /** "Não pagou o valor combinado": vale também depois de {@code CONCLUIDO} ou em {@code AGUARDANDO_VALIDACAO}. */
    NAO_PAGOU_VALOR_COMBINADO,
    /** "Serviço não foi realizado". */
    SERVICO_NAO_REALIZADO,
    /** "Comportamento inadequado". */
    COMPORTAMENTO_INADEQUADO,
    /** "Suspeita de fraude". */
    SUSPEITA_DE_FRAUDE,
    /** "Outro motivo". */
    OUTRO_MOTIVO
}
