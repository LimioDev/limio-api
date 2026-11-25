package com.limio.api.comum.config;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Par encoder/decoder do token de acesso (JWT HS256), com a mesma chave
 * simétrica. O decoder é usado pelo {@code LeitorTokenAcesso}; o encoder, por
 * quem emite token ({@code modulos.auth}).
 *
 * O decoder exige o {@code exp}: o validador padrão só confere a validade quando o claim existe, e um token sem ele
 * (por exemplo, um token de administrador gerado à mão antes da US60) valeria para sempre.
 */
@Configuration
public class JwtConfig {

    // HS256 exige chave de no mínimo 256 bits
    private static final int TAMANHO_MINIMO_SEGREDO_BYTES = 32;

    private final SecretKey chave;

    public JwtConfig(@Value("${security.jwt.secret}") String segredo) {
        byte[] bytes = segredo.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < TAMANHO_MINIMO_SEGREDO_BYTES) {
            throw new IllegalStateException("security.jwt.secret precisa ter ao menos 32 bytes (HS256)");
        }
        this.chave = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return NimbusJwtEncoder.withSecretKey(chave).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull)));
        return decoder;
    }
}
