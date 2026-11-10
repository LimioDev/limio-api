package com.limio.api.modulos.moderacao;

import java.time.Instant;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * Quem vai ser avaliado, para a tela "Avaliação". Conta encerrada aparece como {@link #NOME_CONTA_ENCERRADA}, sem
 * foto, igual ao cabeçalho do chat.
 */
public record OutraParte(String nome, String fotoUrl, Instant fotoExpiraEm, PapelUsuario papel) {

    public static final String NOME_CONTA_ENCERRADA = "Usuário excluído";

    public static OutraParte contaEncerrada(PapelUsuario papel) {
        return new OutraParte(NOME_CONTA_ENCERRADA, null, null, papel);
    }
}
