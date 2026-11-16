package com.limio.api.modulos.financeiro.actions.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Compara o token recebido num webhook com o esperado (ETI-81) em tempo constante
 * ({@link MessageDigest#isEqual}) — comparação de segredo não pode depender do tempo de execução.
 */
public final class WebhookTokenHelper {

    private WebhookTokenHelper() {
    }

    public static boolean valido(String tokenEsperado, String tokenRecebido) {
        if (tokenRecebido == null) {
            return false;
        }
        return MessageDigest.isEqual(tokenEsperado.getBytes(StandardCharsets.UTF_8),
                tokenRecebido.getBytes(StandardCharsets.UTF_8));
    }
}
