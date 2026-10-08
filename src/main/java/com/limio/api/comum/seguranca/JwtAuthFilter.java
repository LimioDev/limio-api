package com.limio.api.comum.seguranca;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.limio.api.comum.seguranca.enums.PapelUsuario;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lê o token de acesso ({@code Authorization: Bearer ...}) e popula o
 * SecurityContext com {@link UsuarioAutenticado}. Token ausente, inválido ou
 * expirado não interrompe a requisição: ela segue anônima e, se a rota for
 * protegida, o entry point do {@code SecurityConfig} responde 401.
 *
 * Não é {@code @Component} de propósito: como bean, o Boot também o
 * registraria como filtro do servlet, rodando fora da cadeia do Spring Security.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Claim com o papel ativo — escrita por quem emite o token ({@code modulos.auth}), lida aqui. */
    public static final String CLAIM_PAPEL = "papel";

    private static final String PREFIXO_BEARER = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtDecoder jwtDecoder;

    public JwtAuthFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecalho != null && cabecalho.startsWith(PREFIXO_BEARER)) {
            autenticar(cabecalho.substring(PREFIXO_BEARER.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void autenticar(String token) {
        UsuarioAutenticado usuario;
        try {
            usuario = lerUsuario(jwtDecoder.decode(token));
        } catch (JwtValidationException e) {
            return; // expirado (ou fora da validade): uso normal do app, segue anônimo sem log
        } catch (JwtException | IllegalArgumentException e) {
            // assinatura inválida, token malformado ou claim inválida: segue anônimo, mas deixa rastro
            // pra distinguir tentativa de forjar token de tráfego normal. Nunca logar o token.
            log.debug("token de acesso recusado ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return;
        }
        if (usuario == null) {
            return;
        }
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(usuario, null, List.of()));
        SecurityContextHolder.setContext(contexto);
    }

    private static UsuarioAutenticado lerUsuario(Jwt jwt) {
        String id = jwt.getSubject();
        String papel = jwt.getClaimAsString(CLAIM_PAPEL);
        if (id == null || papel == null) {
            return null;
        }
        return new UsuarioAutenticado(UUID.fromString(id), PapelUsuario.valueOf(papel));
    }
}
