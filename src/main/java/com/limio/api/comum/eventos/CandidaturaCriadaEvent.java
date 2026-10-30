package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Um Prestador se candidatou a um anúncio (ETI-75). Dois consumidores previstos, nenhum em código ainda:
 * {@code chat} cria o Chat 1:1 ligado à candidatura (ETI-101/ETI-102); {@code notificacao} avisa o
 * Empregador (ETI-112). {@code anuncioTitulo} vai solto (não só {@code anuncioId}) porque quem escuta
 * não pode importar {@code modulos.anuncio} pra buscar o anúncio (ADR-0001); o nome do Prestador fica de
 * fora de propósito — cada consumidor resolve o nome de exibição pela sua própria porta/fonte, do jeito
 * que preferir mostrar.
 */
public record CandidaturaCriadaEvent(
        UUID candidaturaId,
        UUID anuncioId,
        String anuncioTitulo,
        UUID contratanteId,
        UUID prestadorId,
        Instant ocorridoEm) {
}
