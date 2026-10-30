package com.limio.api.modulos.anuncio;

import com.limio.api.modulos.anuncio.aplicacao.Aplicacao;

/**
 * Uma linha da tela "Aplicações" do Prestador (ETI-77). {@code nomeEmpregador} vem abreviado ("Maria S.") e,
 * junto com {@code iniciaisEmpregador}, é {@code null} se a conta do Empregador já foi encerrada.
 */
public record MinhaCandidatura(Aplicacao aplicacao, Anuncio anuncio, String nomeEmpregador,
        String iniciaisEmpregador) {
}
