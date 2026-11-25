package com.limio.api.comum.seguranca;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Token de acesso do administrador para os testes. A API ainda não emite esse token: a rota de login do admin é do
 * card da entrada do admin (US60). O formato é o que o {@link LeitorTokenAcesso} aceita: {@code sub} = id do
 * administrador e {@link JwtAuthFilter#CLAIM_TIPO} = {@link JwtAuthFilter#TIPO_ADMINISTRADOR}, sem papel.
 */
public final class TokensDeTeste {

    private TokensDeTeste() {
    }

    public static String administrador(JwtEncoder encoder, UUID administradorId) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(administradorId.toString())
                .claim(JwtAuthFilter.CLAIM_TIPO, JwtAuthFilter.TIPO_ADMINISTRADOR)
                .issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofMinutes(15)))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
