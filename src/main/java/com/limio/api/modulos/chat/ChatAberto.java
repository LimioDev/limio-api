package com.limio.api.modulos.chat;

import java.util.List;

import com.limio.api.comum.portas.ResumoCandidatura;
import com.limio.api.modulos.chat.mensagem.Mensagem;

/**
 * O que a tela do chat mostra ao abrir (telas 21 e 22): o chat, quem está do outro lado, a candidatura e o anúncio,
 * e uma página do histórico, da mensagem mais nova para a mais antiga.
 */
public record ChatAberto(Chat chat, OutraParte outraParte, ResumoCandidatura candidatura,
        List<Mensagem> mensagens, int tamanho, boolean temMais) {
}
