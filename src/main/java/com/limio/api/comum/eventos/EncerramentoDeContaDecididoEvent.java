package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O administrador decidiu encerrar a conta numa decisão de denúncia (ETI-116). Publicado por {@code moderacao} dentro
 * da transação da decisão. {@code auth} escuta na mesma transação e faz o mesmo encerramento do pedido do próprio
 * usuário (ETI-36: anonimização, CPF bloqueado e sessões encerradas). O {@link ContaEncerradaEvent} que sai de lá
 * leva os outros módulos a reagir depois do commit, como em qualquer encerramento.
 */
public record EncerramentoDeContaDecididoEvent(UUID usuarioId, Instant ocorridoEm) {
}
