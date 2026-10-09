package com.limio.api.modulos.auth.actions.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.limio.api.comum.seguranca.JwtAuthFilter;
import com.limio.api.modulos.auth.actions.helper.RefreshTokenHelper;
import com.limio.api.modulos.auth.sessao.Sessao;
import com.limio.api.modulos.auth.sessao.TokensSessao;
import com.limio.api.modulos.auth.usuario.Usuario;

/**
 * Emite as credenciais de uma {@link Sessao}: token de acesso (JWT curto,
 * lido pelo {@link JwtAuthFilter}) + refresh token (opaco, longo).
 */
@Component
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration validadeTokenAcesso;
    private final Duration validadeRefreshToken;

    public TokenService(JwtEncoder jwtEncoder,
            @Value("${auth.token-acesso.validade}") Duration validadeTokenAcesso,
            @Value("${auth.refresh-token.validade}") Duration validadeRefreshToken) {
        this.jwtEncoder = jwtEncoder;
        this.validadeTokenAcesso = validadeTokenAcesso;
        this.validadeRefreshToken = validadeRefreshToken;
    }

    /**
     * Gera refresh token novo e token de acesso com o papel ativo atual do
     * usuário. Não mexe na sessão — quem chama aplica os tokens com
     * {@link Sessao#rotacionar(TokensSessao, Instant)} e salva.
     */
    public TokensSessao emitir(Usuario usuario, Instant agora) {
        String refreshToken = RefreshTokenHelper.gerar();
        // exp do JWT tem precisão de segundos
        Instant expiraEm = agora.plus(validadeTokenAcesso).truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getId().toString())
                .claim(JwtAuthFilter.CLAIM_PAPEL, usuario.getPapelAtivo().name())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .build();
        String tokenAcesso = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new TokensSessao(tokenAcesso, expiraEm, refreshToken, agora.plus(validadeRefreshToken),
                usuario.getPapelAtivo());
    }
}
