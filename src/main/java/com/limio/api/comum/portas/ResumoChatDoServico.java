package com.limio.api.comum.portas;

import java.util.UUID;

/** Id do chat de uma candidatura e quantas mensagens quem consulta ainda não leu (ETI-86). */
public record ResumoChatDoServico(UUID chatId, int mensagensNaoLidas) {
}
