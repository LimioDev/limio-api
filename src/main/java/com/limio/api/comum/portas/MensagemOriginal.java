package com.limio.api.comum.portas;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem do chat com o texto como o remetente escreveu, antes de qualquer máscara (ETI-116). Só a análise de
 * denúncia do administrador recebe esse texto.
 */
public record MensagemOriginal(UUID id, UUID remetenteId, String texto, Instant enviadaEm) {
}
