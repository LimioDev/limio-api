package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O job expirou o anúncio: data/hora pretendida vencida e nenhuma candidatura (ETI-66, RF30). Consumidor previsto,
 * ainda não em código: {@code notificacao} avisa o Empregador (US56/RF53). Leva {@code contratanteId} e {@code titulo}
 * soltos pra o consumidor montar o aviso sem importar {@code modulos.anuncio}. Não mexe em cota nem há chat a
 * fechar: sem candidatura não houve escolha nem conversa.
 */
public record AnuncioExpiradoEvent(UUID anuncioId, UUID contratanteId, String titulo, Instant ocorridoEm) {
}
