package com.limio.api.modulos.auth.actions.helper;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 do CPF pra uso em {@code cpf_bloqueado} — tabela só confere
 * igualdade (CPF foi excluído há menos de 12 meses?), nunca precisa do valor
 * legível, então não guarda CPF em texto puro.
 */
@Component
public class CpfHasher {

    private static final String ALGORITMO = "HmacSHA256";

    private final SecretKeySpec chave;

    public CpfHasher(@Value("${security.cpf.hmac-secret}") String segredo) {
        this.chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), ALGORITMO);
    }

    public String hash(String cpf) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(chave);
            byte[] digest = mac.doFinal(cpf.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Falha ao gerar hash de CPF", e);
        }
    }
}
