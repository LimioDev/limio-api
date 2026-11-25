package com.limio.api.comum.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * O administrador mandou remover a foto de perfil do denunciado numa decisão procedente (ETI-116, UC "Enviar foto de
 * perfil"). Publicado por {@code moderacao} dentro da transação da decisão. {@code perfil} escuta na mesma transação,
 * tira a foto de uso e apaga o objeto do storage depois do commit. Sem foto, nada acontece.
 */
public record FotoPerfilRemovidaPelaModeracaoEvent(UUID usuarioId, Instant ocorridoEm) {
}
