package com.limio.api.modulos.auth.actions.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Token de verificação de contato (recuperação de senha e confirmação de e-mail), opaco: 256 bits
 * aleatórios em base64url. No banco vai só o SHA-256 — mesma lógica do {@link RefreshTokenHelper}, token
 * de outra família (por isso a classe própria, em vez de reaproveitar aquela).
 */
public final class TokenVerificacaoHelper {

    private static final int TAMANHO_BYTES = 32;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenVerificacaoHelper() {
    }

    public static String gerar() {
        byte[] bytes = new byte[TAMANHO_BYTES];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash do e-mail para onde o link foi enviado (D10 do TICKET-0048): o token só vale enquanto a conta
     * tiver esse mesmo e-mail. Normaliza como o cadastro (trim + minúsculas), pra caixa não importar.
     */
    public static String hashDestino(String email) {
        return hash(email.trim().toLowerCase(Locale.ROOT));
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
