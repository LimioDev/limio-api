package com.limio.api.comum.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.limio.api.comum.seguranca.JwtAuthFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // usa o CorsConfigurationSource do CorsConfig; responde o preflight (OPTIONS) antes da autenticação
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // As páginas web lidam com token de link de e-mail: só script/estilo do próprio domínio (um XSS
                // não consegue mandar o token pra fora) e nenhum Referer saindo delas.
                .headers(cabecalhos -> cabecalhos
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        // públicas: quem chama ainda não tem token de acesso válido.
                        .requestMatchers(HttpMethod.POST, "/auth/cadastro", "/auth/login", "/auth/refresh",
                                "/auth/recuperar-senha", "/auth/redefinir-senha",
                                "/auth/confirmar-email", "/auth/reenviar-confirmacao-email").permitAll()
                        // páginas web abertas pelos links dos e-mails (TICKET-0047, ADR-0002) e seus arquivos
                        .requestMatchers(HttpMethod.GET, PaginasWebConfig.ROTAS_PUBLICAS).permitAll()
                        // health check do Railway: sem isso todo deploy falha com 401
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // handshake do canal em tempo real: o token é conferido no CONNECT (WebSocketAuthInterceptor)
                        .requestMatchers(WebSocketConfig.ROTA_WEBSOCKET).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtDecoder), UsernamePasswordAuthenticationFilter.class)
                // 401/403 saem pelo GlobalExceptionHandler como qualquer outro erro (ErroResponse + NAO_AUTENTICADO/ACESSO_NEGADO)
                .exceptionHandling(erro -> erro
                        .authenticationEntryPoint(
                                (request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex))
                        .accessDeniedHandler(
                                (request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex)));
        return http.build();
    }
}
