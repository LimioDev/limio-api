package com.limio.api.modulos.anuncio.records;

import java.time.Instant;

/**
 * A outra parte do serviço combinado (ETI-86). {@code nome} e {@code telefone} saem completos — contato
 * liberado. {@code gold} é {@code null} quando quem consulta é o Prestador (não existe selo do Empregador).
 * Tudo {@code null} se a conta já foi encerrada.
 */
public record OutraParteServicoResponse(String nome, String fotoUrl, Instant fotoExpiraEm, String telefone,
        Boolean gold) {
}
