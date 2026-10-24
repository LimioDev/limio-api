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
}
