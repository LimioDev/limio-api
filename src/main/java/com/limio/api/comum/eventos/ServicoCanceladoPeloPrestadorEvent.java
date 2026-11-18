package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O Prestador escolhido cancelou o serviço combinado (ETI-96). Publicado dentro da transação do cancelamento; quem
 * reage roda só depois do commit (ADR-0003). Consumidores: {@code chat} deixa somente leitura o chat da candidatura
 * escolhida; {@code notificacao} avisará o Empregador ({@code contratanteId}, US56/ETI-112), nenhum em código ainda.
 *
 * <p>Evento próprio, e não o {@link ServicoCanceladoEvent}, porque aquele é do Empregador (carrega tipo, estorno de
 * cota e data vigente, nada disso se aplica aqui: a cota não é estornada, RB18). Quem cancela é sempre
 * {@code prestadorId}. Título e ids vão soltos porque quem escuta não pode importar {@code modulos.anuncio}.
 */
public record ServicoCanceladoPeloPrestadorEvent(
        UUID anuncioId,
        String anuncioTitulo,
        UUID candidaturaEscolhidaId,
        UUID contratanteId,
        UUID prestadorId,
        Instant ocorridoEm) {
}
