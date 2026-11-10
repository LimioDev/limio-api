package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * O Empregador escolheu um Prestador para o anúncio (ETI-69). Publicado dentro da transação da escolha; quem reage
 * roda só depois do commit (ADR-0003). Consumidores: {@code chat} deixa somente leitura os chats das candidaturas em
 * {@code candidaturasNaoEscolhidasIds} (o do escolhido não é tocado); {@code notificacao} avisará o escolhido e os
 * não escolhidos (ETI-112), nenhum em código ainda.
 *
 * <p>{@code candidaturasNaoEscolhidasIds} são as que esta escolha encerrou (estavam {@code ENVIADA}); as já recusadas
 * ou encerradas antes ficam de fora, seus chats já estão fechados. Título e ids vão soltos porque quem escuta não
 * pode importar {@code modulos.anuncio}.
 */
public record PrestadorEscolhidoEvent(
        UUID anuncioId,
        String anuncioTitulo,
        UUID candidaturaEscolhidaId,
        UUID prestadorId,
        UUID contratanteId,
        List<UUID> candidaturasNaoEscolhidasIds,
        Instant ocorridoEm) {

    public PrestadorEscolhidoEvent {
        candidaturasNaoEscolhidasIds = List.copyOf(candidaturasNaoEscolhidasIds);
    }
}
