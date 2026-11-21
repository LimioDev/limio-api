package com.limio.api.modulos.anuncio.records;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A outra parte do serviço combinado (ETI-86). {@code nome} e {@code telefone} saem completos — contato
 * liberado. {@code gold} é {@code null} quando quem consulta é o Prestador (não existe selo do Empregador).
 * {@code qtdAvaliacoes}/{@code mediaAvaliacoes} (mesmos nomes do {@code CandidatoResponse}, ETI-138) seguem a regra
 * do {@code gold}: só pro Empregador vendo o Prestador; {@code mediaAvaliacoes} é {@code null} sem avaliação.
 * Tudo {@code null} se a conta já foi encerrada.
 */
public record OutraParteServicoResponse(String nome, String fotoUrl, Instant fotoExpiraEm, String telefone,
        Boolean gold, Integer qtdAvaliacoes, BigDecimal mediaAvaliacoes) {
}
