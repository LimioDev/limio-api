package com.limio.api.comum.seguranca;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
 * {@link UsuarioAutenticado} (role {@link #ROLE_USUARIO}) ou, no painel web,
 * {@link AdministradorAutenticado} (role {@link #ROLE_ADMINISTRADOR}). Token
 * ausente, inválido ou expirado não interrompe a requisição: ela segue anônima
 * e, se a rota for protegida, o entry point do {@code SecurityConfig} responde 401.
 *
 * Não é {@code @Component} de propósito: como bean, o Boot também o
 * registraria como filtro do servlet, rodando fora da cadeia do Spring Security.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Claim com o papel ativo — escrita por quem emite o token ({@code modulos.auth}), lida aqui. */
    public static final String CLAIM_PAPEL = "papel";

    /** Claim que separa o token do administrador (ETI-117). Token de usuário não a traz. */
    public static final String CLAIM_TIPO = "tipo";

    /** Valor de {@link #CLAIM_TIPO} no token do administrador. */
    public static final String TIPO_ADMINISTRADOR = "ADMINISTRADOR";

    /** Role de quem usa o app (EMPREGADOR ou PRESTADOR), para {@code hasRole} no {@code SecurityConfig}. */
    public static final String ROLE_USUARIO = "USUARIO";

    /** Role do administrador do painel web, para {@code hasRole} no {@code SecurityConfig}. */
    public static final String ROLE_ADMINISTRADOR = "ADMINISTRADOR";

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
        Optional<UsernamePasswordAuthenticationToken> autenticacao = leitorTokenAcesso.ler(token)
                .map(acesso -> autenticado(acesso.usuario(), ROLE_USUARIO))
                .or(() -> leitorTokenAcesso.lerAdministrador(token)
                        .map(administrador -> autenticado(administrador, ROLE_ADMINISTRADOR)));
        autenticacao.ifPresent(autenticado -> {
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticado);
            SecurityContextHolder.setContext(contexto);
        });
    }

    private static UsernamePasswordAuthenticationToken autenticado(Principal principal, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
