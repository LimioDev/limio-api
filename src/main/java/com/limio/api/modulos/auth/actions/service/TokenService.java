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
     * Abre ou rotaciona a sessão: gera refresh token novo (o hash anterior é
     * substituído, então o token antigo deixa de valer), empurra a expiração
     * pra frente e emite token de acesso com o papel ativo atual do usuário.
     * Não persiste — quem chama salva a sessão.
     */
    public TokensSessao emitir(Sessao sessao, Instant agora) {
        String refreshToken = RefreshTokenHelper.gerar();
        sessao.setRefreshTokenHash(RefreshTokenHelper.hash(refreshToken));
        sessao.setUltimoUsoEm(agora);
        sessao.setExpiraEm(agora.plus(validadeRefreshToken));

        Usuario usuario = sessao.getUsuario();
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

        return new TokensSessao(tokenAcesso, expiraEm, refreshToken, usuario.getPapelAtivo());
    }
}
