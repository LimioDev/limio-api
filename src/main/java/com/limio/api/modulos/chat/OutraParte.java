package com.limio.api.modulos.chat;

import java.time.Instant;

import com.limio.api.modulos.chat.enums.PapelNoChat;

/**
 * Quem está do outro lado da conversa, para o cabeçalho. Conta encerrada aparece como {@link #NOME_CONTA_ENCERRADA},
 * sem foto: o histórico fica, a identidade não.
 */
public record OutraParte(String nome, String fotoUrl, Instant fotoExpiraEm, PapelNoChat papel) {

    public static final String NOME_CONTA_ENCERRADA = "Usuário excluído";

    public static OutraParte contaEncerrada(PapelNoChat papel) {
        return new OutraParte(NOME_CONTA_ENCERRADA, null, null, papel);
    }
}
