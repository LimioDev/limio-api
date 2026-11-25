package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O administrador suspendeu a conta numa decisão de denúncia (ETI-116). Publicado por {@code moderacao} dentro da
 * transação da decisão.
 *
 * <ul>
 *   <li>{@code auth} escuta na mesma transação: a conta fica {@code BLOQUEADA} até {@code suspensaAte} e as sessões
 *       são encerradas. Se isso falhar, a decisão inteira desfaz.</li>
 *   <li>{@code anuncio} escuta depois do commit: encerra os anúncios, as candidaturas e os combinados abertos e as
 *       propostas de reagendamento pendentes, como no encerramento de conta (ETI-46).</li>
 * </ul>
 *
 * @param suspensaAte fim da suspensão; depois dele a conta volta a {@code ATIVA}
 */
public record ContaSuspensaEvent(UUID usuarioId, Instant suspensaAte, Instant ocorridoEm) {
}
