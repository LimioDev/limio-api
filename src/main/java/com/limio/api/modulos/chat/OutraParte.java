package com.limio.api.modulos.chat;

import java.time.Instant;
import java.util.UUID;

import com.limio.api.modulos.chat.enums.PapelNoChat;

/**
 * Quem está do outro lado da conversa, para o cabeçalho. Conta encerrada aparece como {@link #NOME_CONTA_ENCERRADA},
 * sem foto: o histórico fica, a identidade não. {@code usuarioId} é o da outra pessoa, pro app abrir o perfil
 * público dela (ETI-136).
 */
public record OutraParte(UUID usuarioId, String nome, String fotoUrl, Instant fotoExpiraEm, PapelNoChat papel) {

    public static final String NOME_CONTA_ENCERRADA = "Usuário excluído";

    /** O {@code usuarioId} continua: o perfil público da conta encerrada mostra "Conta encerrada" e o histórico. */
    public static OutraParte contaEncerrada(UUID usuarioId, PapelNoChat papel) {
        return new OutraParte(usuarioId, NOME_CONTA_ENCERRADA, null, null, papel);
    }
}
