package com.limio.api.comum.seguranca;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lê o token de acesso ({@code Authorization: Bearer ...}) com o
 * {@link LeitorTokenAcesso} e popula o SecurityContext com
 * {@link UsuarioAutenticado}. Token ausente, inválido ou expirado não
 * interrompe a requisição: ela segue anônima e, se a rota for protegida, o
 * entry point do {@code SecurityConfig} responde 401.
 *
 * Não é {@code @Component} de propósito: como bean, o Boot também o
 * registraria como filtro do servlet, rodando fora da cadeia do Spring Security.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Claim com o papel ativo — escrita por quem emite o token ({@code modulos.auth}), lida aqui. */
    public static final String CLAIM_PAPEL = "papel";

    private static final String PREFIXO_BEARER = "Bearer ";

    private final LeitorTokenAcesso leitorTokenAcesso;

    public JwtAuthFilter(LeitorTokenAcesso leitorTokenAcesso) {
        this.leitorTokenAcesso = leitorTokenAcesso;
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
        leitorTokenAcesso.ler(token).ifPresent(acesso -> {
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(acesso.usuario(), null, List.of()));
            SecurityContextHolder.setContext(contexto);
        });
    }
}
