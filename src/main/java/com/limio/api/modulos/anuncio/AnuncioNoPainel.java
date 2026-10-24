package com.limio.api.modulos.anuncio;

/**
 * Um anúncio do painel do Empregador (TICKET-0052). {@code candidatos} conta as candidaturas
 * {@code ENVIADA}; {@code prestadorEscolhido} é o nome abreviado de quem foi escolhido, {@code null}
 * enquanto ninguém foi.
 */
public record AnuncioNoPainel(Anuncio anuncio, int candidatos, String prestadorEscolhido) {
}
