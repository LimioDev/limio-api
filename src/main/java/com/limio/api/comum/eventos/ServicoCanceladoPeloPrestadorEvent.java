package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O Prestador escolhido cancelou o serviço combinado (ETI-96). Publicado dentro da transação do cancelamento; quem
 * reage roda só depois do commit (ADR-0003). Consumidor hoje: {@code chat}, que deixa somente leitura o chat da
 * candidatura escolhida. O aviso ao Empregador ({@code contratanteId}) fica para o {@code notificacao}, junto com o do
 * {@link ServicoCanceladoEvent}, que o ETI-112 também deixou de fora.
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
