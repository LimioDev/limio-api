package com.limio.api.modulos.chat;

import java.time.Instant;
import java.util.UUID;

public interface ConversaProjecao {

    UUID getChatId();

    UUID getCandidaturaId();

    UUID getContratanteId();

    UUID getPrestadorId();

    boolean getSomenteLeitura();

    String getMotivoSomenteLeitura();

    String getUltimaMensagem();

    Instant getUltimaMensagemEm();

    UUID getUltimaMensagemRemetenteId();

    long getNaoLidas();
}
