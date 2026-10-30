package com.limio.api.comum.seguranca;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Component;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

/**
 * Confere o token de acesso e diz quem é o dono. Usado pelas rotas HTTP ({@link JwtAuthFilter}) e pelo CONNECT do
 * canal em tempo real ({@link WebSocketAuthInterceptor}).
 */
@Component
public class LeitorTokenAcesso {

    private static final Logger log = LoggerFactory.getLogger(LeitorTokenAcesso.class);

    private final JwtDecoder jwtDecoder;

    public LeitorTokenAcesso(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    /** Vazio se o token é inválido, expirado ou não traz id e papel. */
    public Optional<TokenAcesso> ler(String token) {
        try {
            return lerUsuario(jwtDecoder.decode(token));
        } catch (JwtValidationException e) {
            return Optional.empty(); // expirado (ou fora da validade): uso normal do app, segue anônimo sem log
        } catch (JwtException | IllegalArgumentException e) {
            // assinatura inválida, token malformado ou claim inválida: segue anônimo, mas deixa rastro
            // pra distinguir tentativa de forjar token de tráfego normal. Nunca logar o token.
            log.debug("token de acesso recusado ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<TokenAcesso> lerUsuario(Jwt jwt) {
        String id = jwt.getSubject();
        String papel = jwt.getClaimAsString(JwtAuthFilter.CLAIM_PAPEL);
        if (id == null || papel == null) {
            return Optional.empty();
        }
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.fromString(id), PapelUsuario.valueOf(papel));
        return Optional.of(new TokenAcesso(usuario, jwt.getExpiresAt()));
    }
}
