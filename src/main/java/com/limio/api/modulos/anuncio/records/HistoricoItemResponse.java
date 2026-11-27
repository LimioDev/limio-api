package com.limio.api.modulos.anuncio.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.anuncio.enums.StatusHistorico;

/**
 * Uma linha do histórico (ETI-71). De propósito sem telefone, e-mail, endereço ou coordenadas: a outra parte aparece
 * só pelo nome ({@code outraParte}), então não há contato a vazar. {@code outraParte} é {@code null} quando o anúncio
 * nunca teve ninguém escolhido (Empregador: cancelado antes da escolha ou expirado). {@code avaliacao} só vem em
 * serviço {@code CONCLUIDO} do qual quem consulta participou; nos demais é {@code null}. {@code data} é o dia do
 * desfecho (conclusão, cancelamento ou expiração).
 */
public record HistoricoItemResponse(
        UUID anuncioId,
        String titulo,
        Instant data,
        StatusHistorico status,
        String outraParte,
        AvaliacaoHistoricoResponse avaliacao) {
}
