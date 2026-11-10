package com.limio.api.comum.texto;

import java.util.Locale;

/**
 * Nome de pessoa como aparece pra outra parte — anúncio ({@code modulos.anuncio}) e perfil público
 * ({@code modulos.perfil}, ETI-105). Promovido pra {@code comum/} quando o segundo módulo passou a precisar
 * da mesma regra (critério do ADR-0001: usado por 2+ módulos hoje).
 */
public final class NomeHelper {

    private NomeHelper() {
    }

    /**
     * Primeiro nome + inicial do último sobrenome ({@code "Rafael da Silva Souza"} → {@code "Rafael S."}),
     * pra não expor o nome completo no painel e na tela 16. Nome de uma palavra só sai como está.
     */
    public static String abreviar(String nomeCompleto) {
        String[] partes = partes(nomeCompleto);
        if (partes.length == 1)
            return partes[0];
        String ultimo = partes[partes.length - 1];
        return partes[0] + " " + inicial(ultimo) + ".";
    }

    /**
     * Inicial do primeiro nome + do último sobrenome ({@code "Maria da Silva"} → {@code "MS"}), pro avatar sem
     * foto (ETI-77). Nome de uma palavra só sai com uma letra.
     */
    public static String iniciais(String nomeCompleto) {
        String[] partes = partes(nomeCompleto);
        if (partes.length == 1)
            return inicial(partes[0]);
        return inicial(partes[0]) + inicial(partes[partes.length - 1]);
    }

    private static String[] partes(String nomeCompleto) {
        return nomeCompleto.strip().split("\\s+");
    }

    private static String inicial(String palavra) {
        return palavra.substring(0, 1).toUpperCase(Locale.ROOT);
    }
}
