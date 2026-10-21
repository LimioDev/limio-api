package com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.enums;

/**
 * Dia de uma janela de atendimento. Nome por extenso no JSON e no banco (nunca número): resolve a
 * pendência "0–6 ou 1–7?" do TICKET-0054 sem convenção implícita entre back e front. A ordem das
 * constantes (segunda primeiro, como ISO-8601) é a ordem de exibição das janelas.
 */
public enum DiaSemana {
    SEGUNDA,
    TERCA,
    QUARTA,
    QUINTA,
    SEXTA,
    SABADO,
    DOMINGO
}
