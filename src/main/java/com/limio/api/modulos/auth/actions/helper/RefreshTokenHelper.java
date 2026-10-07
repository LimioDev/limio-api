package com.limio.api.modulos.auth.actions.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh token opaco: 256 bits aleatórios em base64url. No banco vai só o
 * SHA-256 — com essa entropia não precisa de salt nem BCrypt, e o hash
 * determinístico permite achar a sessão direto pelo índice.
 */
public final class RefreshTokenHelper {

    private static final int TAMANHO_BYTES = 32;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private RefreshTokenHelper() {
    }

    public static String gerar() {
        byte[] bytes = new byte[TAMANHO_BYTES];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
