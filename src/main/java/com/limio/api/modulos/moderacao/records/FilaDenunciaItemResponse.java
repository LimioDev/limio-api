package com.limio.api.modulos.moderacao.records;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.moderacao.enums.MotivoDenuncia;
import com.limio.api.modulos.moderacao.enums.Severidade;

/**
 * Uma linha da fila de denúncias (ETI-116).
 *
 * @param denunciadoNome nulo se o id nunca existiu; "Conta encerrada" se a conta foi encerrada
 */
public record FilaDenunciaItemResponse(UUID id, Long numero, MotivoDenuncia motivo, Severidade severidade,
        UUID denunciadoId, String denunciadoNome, Instant prazoAnalise, Instant criadaEm) {
}
