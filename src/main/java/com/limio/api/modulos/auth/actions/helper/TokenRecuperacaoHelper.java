package com.limio.api.modulos.auth.actions.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token de recuperação de senha, opaco: 256 bits aleatórios em base64url. No
 * banco vai só o SHA-256 — mesma lógica do {@link RefreshTokenHelper}, token
 * de outra família (por isso a classe própria, em vez de reaproveitar
 * aquela).
 */
public final class TokenRecuperacaoHelper {

    private static final int TAMANHO_BYTES = 32;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private TokenRecuperacaoHelper() {
    }

    public static String gerar() {
        byte[] bytes = new byte[TAMANHO_BYTES];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
