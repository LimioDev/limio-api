package com.limio.api.modulos.anuncio.actions.helper;

import java.util.Locale;

/** Nome de pessoa como aparece pra outra parte de um anúncio. */
public final class NomeHelper {

    private NomeHelper() {
    }

    /**
     * Primeiro nome + inicial do último sobrenome ({@code "Rafael da Silva Souza"} → {@code "Rafael S."}),
     * pra não expor o nome completo no painel. Nome de uma palavra só sai como está.
     */
    public static String abreviar(String nomeCompleto) {
        String[] partes = nomeCompleto.strip().split("\\s+");
        if (partes.length == 1)
            return partes[0];
        String ultimo = partes[partes.length - 1];
        return partes[0] + " " + ultimo.substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }

    /**
     * Iniciais do primeiro nome e do último sobrenome ({@code "Rafael da Silva Souza"} → {@code "RS"}), pro
     * avatar de quem não tem foto. Nome de uma palavra só sai com uma letra.
     */
    public static String iniciais(String nomeCompleto) {
        String[] partes = nomeCompleto.strip().split("\\s+");
        String primeira = partes[0].substring(0, 1);
        String ultima = partes.length == 1 ? "" : partes[partes.length - 1].substring(0, 1);
        return (primeira + ultima).toUpperCase(Locale.ROOT);
    }
}
