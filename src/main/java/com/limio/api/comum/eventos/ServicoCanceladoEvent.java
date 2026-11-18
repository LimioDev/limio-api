package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.comum.eventos.enums.TipoCancelamento;

/**
 * O Empregador cancelou o serviço combinado, com a antecedência mínima (ETI-94). Publicado dentro da transação do
 * cancelamento; quem reage roda só depois do commit (ADR-0003). Consumidores: {@code chat} deixa somente leitura o
 * chat da candidatura escolhida; {@code notificacao} avisará o Prestador (ETI-112), nenhum em código ainda. O estorno
 * da cota NÃO é reação a este evento: acontece na mesma transação, pela porta {@code ConsumoCotaPrestador}, e {@code
 * cotaEstornada} só informa o resultado.
 *
 * <p>Quem cancela é sempre {@code contratanteId} (só o dono do anúncio pode), por isso não há campo próprio.
 * {@code dataHoraVigente} é a data/hora do serviço no momento do cancelamento (a de um reagendamento aceito, se
 * houver). Título e ids vão soltos porque quem escuta não pode importar {@code modulos.anuncio}.
 */
public record ServicoCanceladoEvent(
        UUID anuncioId,
        String anuncioTitulo,
        UUID candidaturaEscolhidaId,
        UUID contratanteId,
        UUID prestadorId,
        TipoCancelamento tipo,
        boolean cotaEstornada,
        Instant dataHoraVigente,
        Instant ocorridoEm) {
}
